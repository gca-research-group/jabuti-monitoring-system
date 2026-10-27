package br.edu.unijui.gca.api.pipeline.execution;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.pipeline.execution.application.ExecuteContract;
import br.edu.unijui.gca.api.pipeline.execution.application.ExecutionRejectedException;
import br.edu.unijui.gca.api.pipeline.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExecutionFailureTests {

    private final ExecutionStore store = mock(ExecutionStore.class);
    private final BlockchainStore blockchains = mock(BlockchainStore.class);
    private final ContractInvoker invoker = mock(ContractInvoker.class);
    private final ExecutionEventPublisher publisher = mock(ExecutionEventPublisher.class);
    private final ExecuteContract application = new ExecuteContract(store, blockchains, invoker, publisher);
    private final SmartContractPayloadDto payload = SmartContractPayloadDto.builder().id(UUID.randomUUID())
            .blockchainId(UUID.randomUUID()).smartContractId(UUID.randomUUID()).build();
    private final SmartContractExecution execution = new SmartContractExecution();
    private final Blockchain blockchain = new Blockchain();

    @BeforeEach
    void existingExecution() {
        execution.setId(payload.getId());
        execution.setStatus(SmartContractExecutionStatus.PROCESSING);
        execution.setTimestamps(new HashMap<>());
        when(store.findById(payload.getId())).thenReturn(execution);
        when(blockchains.findById(payload.getBlockchainId())).thenReturn(blockchain);
        when(invoker.invoke(blockchain, payload)).thenReturn("receipt-42");
    }

    @Test
    void invocationFailureStoresFailureAndPublishesOutcomeBeforeRejecting() {
        var failure = new IllegalStateException("invocation failed");
        when(invoker.invoke(blockchain, payload)).thenThrow(failure);

        assertThatThrownBy(() -> application.process(payload)).isInstanceOf(ExecutionRejectedException.class).hasCause(failure);
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
        assertThat(execution.getResult()).isEqualTo("invocation failed");
        verify(store, times(2)).update(execution);
        verify(publisher).publishOutcome(execution);
    }

    @Test
    void publicationFailureAfterInvocationRetainsTheSecondFailureOutcomeWithoutReinvoking() {
        var failure = new IllegalStateException("outcome publication failed");
        var attemptedStates = new ArrayList<SmartContractExecutionStatus>();
        var attemptedResults = new ArrayList<String>();
        doAnswer(call -> {
            attemptedStates.add(execution.getStatus());
            attemptedResults.add(execution.getResult());
            if (attemptedStates.size() == 1) {
                throw failure;
            }
            return null;
        }).when(publisher).publishOutcome(execution);

        assertThatThrownBy(() -> application.process(payload)).isInstanceOf(ExecutionRejectedException.class).hasCause(failure);
        assertThat(attemptedStates).containsExactly(SmartContractExecutionStatus.PROCESSED, SmartContractExecutionStatus.FAILED);
        assertThat(attemptedResults).containsExactly("receipt-42", "outcome publication failed");
        verify(invoker).invoke(blockchain, payload);
        verify(store, times(3)).update(execution);
    }

    @Test
    void executionLookupFailureEscapesBeforeFailureHandling() {
        var failure = new IllegalStateException("execution unavailable");
        when(store.findById(payload.getId())).thenThrow(failure);

        assertThatThrownBy(() -> application.process(payload)).isSameAs(failure);
        verifyNoInteractions(blockchains, invoker, publisher);
    }

    @Test
    void initialStateUpdateFailurePublishesFailureWithoutInvokingBlockchain() {
        var failure = new IllegalStateException("state update failed");
        doThrow(failure).doNothing().when(store).update(execution);

        assertThatThrownBy(() -> application.process(payload)).isInstanceOf(ExecutionRejectedException.class).hasCause(failure);
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
        verifyNoInteractions(blockchains, invoker);
        verify(publisher).publishOutcome(execution);
    }

    @Test
    void failureStatePersistenceErrorEscapesWithoutPublishing() {
        when(invoker.invoke(blockchain, payload)).thenThrow(new IllegalStateException("invocation failed"));
        var failure = new IllegalStateException("failure state not saved");
        doNothing().doThrow(failure).when(store).update(execution);

        assertThatThrownBy(() -> application.process(payload)).isSameAs(failure);
        verifyNoInteractions(publisher);
    }

    @Test
    void failureOutcomePublicationErrorEscapesWithoutBecomingAHandledRejection() {
        when(invoker.invoke(blockchain, payload)).thenThrow(new IllegalStateException("invocation failed"));
        var failure = new IllegalStateException("failure outcome not published");
        doThrow(failure).when(publisher).publishOutcome(execution);

        assertThatThrownBy(() -> application.process(payload)).isSameAs(failure);
    }
}
