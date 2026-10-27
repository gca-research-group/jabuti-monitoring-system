package br.edu.unijui.gca.api.pipeline.preparation;

import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.exceptions.InvalidBlockchainPlatformException;
import br.edu.unijui.gca.api.exceptions.InvalidSmartContractClauseArgumentException;
import br.edu.unijui.gca.api.exceptions.InvalidSmartContractClauseException;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.pipeline.preparation.application.PreparationRejectedException;
import br.edu.unijui.gca.api.pipeline.preparation.application.PrepareExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PreparationFailureTests {

    private final ExecutionStore store = mock(ExecutionStore.class);
    private final BlockchainStore blockchains = mock(BlockchainStore.class);
    private final SmartContractStore contracts = mock(SmartContractStore.class);
    private final ExecutionEventPublisher publisher = mock(ExecutionEventPublisher.class);
    private final PrepareExecution preparation = new PrepareExecution(store, blockchains, contracts, publisher);
    private final SmartContractQueueInboundEventDto event = new SmartContractQueueInboundEventDto();
    private final SmartContractExecution execution = new SmartContractExecution();
    private final SmartContract contract = new SmartContract();

    @BeforeEach
    void validPreparation() {
        event.setId(UUID.randomUUID());
        event.setBlockchainId(UUID.randomUUID());
        event.setSmartContractId(UUID.randomUUID());
        event.setClauseName("recordReading");
        event.setClauseArguments(List.of(new SmartContractClauseArgumentDto("reading", "42")));
        event.setTimestamps(new HashMap<>());
        execution.setId(event.getId());
        execution.setTimestamps(event.getTimestamps());
        var blockchain = new Blockchain();
        blockchain.setId(event.getBlockchainId());
        blockchain.setPlatform(BlockchainPlatform.HYPERLEDGER_FABRIC);
        contract.setId(event.getSmartContractId());
        contract.setBlockchainPlatform(BlockchainPlatform.HYPERLEDGER_FABRIC);
        contract.setClauses(List.of(new SmartContractClauseDto("recordReading",
                List.of(new SmartContractClauseArgumentDto("reading", null)), List.of())));
        when(store.create(any(SmartContractExecutionDto.class))).thenReturn(execution);
        when(blockchains.findById(event.getBlockchainId())).thenReturn(blockchain);
        when(contracts.findById(event.getSmartContractId())).thenReturn(contract);
    }

    @ParameterizedTest
    @ValueSource(strings = {"platform", "clause", "argument"})
    void invalidMetadataMarksExecutionFailedPublishesOutcomeAndRejects(String invalid) {
        Class<? extends RuntimeException> cause;
        switch (invalid) {
            case "platform" -> {
                contract.setBlockchainPlatform(BlockchainPlatform.DUMMY);
                cause = InvalidBlockchainPlatformException.class;
            }
            case "clause" -> {
                event.setClauseName("unknown");
                cause = InvalidSmartContractClauseException.class;
            }
            default -> {
                event.setClauseArguments(List.of(new SmartContractClauseArgumentDto("unknown", "42")));
                cause = InvalidSmartContractClauseArgumentException.class;
            }
        }

        assertThatThrownBy(() -> preparation.process(event)).isInstanceOf(PreparationRejectedException.class)
                .hasCauseInstanceOf(cause);
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
        assertThat(execution.getResult()).isEqualTo(invalid.equals("platform")
                ? "INVALID_BLOCKCHAIN_PLATFORM" : "INVALID_SMART_CONTRACT_CLAUSE");
        verify(store, times(2)).update(execution);
        verify(publisher).publishOutcome(execution);
        verify(publisher, never()).publishExecution(any());
    }

    @Test
    void executionPublicationFailureRetainsFailureOutcomeAndOriginalRejectionCause() {
        var failure = new IllegalStateException("execution publication failed");
        doThrow(failure).when(publisher).publishExecution(any());

        assertThatThrownBy(() -> preparation.process(event)).isInstanceOf(PreparationRejectedException.class)
                .hasCause(failure);
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
        assertThat(execution.getResult()).isEqualTo("execution publication failed");
        verify(store, times(3)).update(execution);
        verify(publisher).publishOutcome(execution);
    }

    @Test
    void initialCreationFailureEscapesWithoutPublishingAnOutcome() {
        var failure = new IllegalStateException("creation failed");
        when(store.create(any(SmartContractExecutionDto.class))).thenThrow(failure);

        assertThatThrownBy(() -> preparation.process(event)).isSameAs(failure);
        verifyNoInteractions(blockchains, contracts, publisher);
    }

    @Test
    void failureOutcomePublicationErrorEscapesWithoutBecomingAHandledRejection() {
        contract.setBlockchainPlatform(BlockchainPlatform.DUMMY);
        var failure = new IllegalStateException("outcome publication failed");
        doThrow(failure).when(publisher).publishOutcome(execution);

        assertThatThrownBy(() -> preparation.process(event)).isSameAs(failure);
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
    }

    @Test
    void failurePersistenceErrorEscapesBeforePublishingAnOutcome() {
        contract.setBlockchainPlatform(BlockchainPlatform.DUMMY);
        var failure = new IllegalStateException("failure state not saved");
        doNothing().doThrow(failure).when(store).update(execution);

        assertThatThrownBy(() -> preparation.process(event)).isSameAs(failure);
        verifyNoInteractions(publisher);
    }
}
