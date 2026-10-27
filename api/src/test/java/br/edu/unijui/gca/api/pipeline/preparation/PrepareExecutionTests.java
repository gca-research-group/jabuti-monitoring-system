package br.edu.unijui.gca.api.pipeline.preparation;

import br.edu.unijui.gca.api.dtos.*;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.pipeline.preparation.application.PrepareExecution;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrepareExecutionTests {

    private final ExecutionStore store = mock(ExecutionStore.class);
    private final BlockchainStore blockchains = mock(BlockchainStore.class);
    private final SmartContractStore contracts = mock(SmartContractStore.class);
    private final ExecutionEventPublisher publisher = mock(ExecutionEventPublisher.class);

    @Test
    void validEventIsPreparedAndPublishedWithExistingStateAndTimestampSemantics() {
        var event = new SmartContractQueueInboundEventDto();
        event.setId(UUID.randomUUID());
        event.setBlockchainId(UUID.randomUUID());
        event.setSmartContractId(UUID.randomUUID());
        event.setClauseName("recordReading");
        event.setClauseArguments(List.of(new SmartContractClauseArgumentDto("reading", "42")));
        event.setMetadata(Map.of("sensor", "temperature"));
        String publishedAt = "2026-10-27T10:00:00Z";
        event.setTimestamps(new HashMap<>(Map.of(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED, publishedAt)));
        var blockchain = new Blockchain();
        blockchain.setId(event.getBlockchainId());
        blockchain.setPlatform(BlockchainPlatform.HYPERLEDGER_FABRIC);
        var contract = new SmartContract();
        contract.setId(event.getSmartContractId());
        contract.setBlockchainPlatform(BlockchainPlatform.HYPERLEDGER_FABRIC);
        contract.setClauses(List.of(new SmartContractClauseDto("recordReading",
                List.of(new SmartContractClauseArgumentDto("reading", null)), List.of())));
        var execution = new SmartContractExecution();
        execution.setId(event.getId());
        execution.setTimestamps(event.getTimestamps());
        var states = new ArrayList<SmartContractExecutionStatus>();
        when(store.create(any(SmartContractExecutionDto.class))).thenReturn(execution);
        doAnswer(call -> { states.add(execution.getStatus()); return null; }).when(store).update(execution);
        when(blockchains.findById(event.getBlockchainId())).thenReturn(blockchain);
        when(contracts.findById(event.getSmartContractId())).thenReturn(contract);
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        new PrepareExecution(store, blockchains, contracts, publisher).process(event);

        var created = ArgumentCaptor.forClass(SmartContractExecutionDto.class);
        verify(store).create(created.capture());
        assertThat(created.getValue().getId()).isEqualTo(event.getId());
        assertThat(created.getValue().getStatus()).isEqualTo(SmartContractExecutionStatus.PENDING);
        assertThat(created.getValue().getMetadata()).isEqualTo(event.getMetadata());
        assertThat(states).containsExactly(SmartContractExecutionStatus.PROCESSING, SmartContractExecutionStatus.PROCESSING);
        var payload = ArgumentCaptor.forClass(SmartContractPayloadDto.class);
        verify(publisher).publishExecution(payload.capture());
        assertThat(payload.getValue().getId()).isEqualTo(event.getId());
        assertThat(payload.getValue().getBlockchainId()).isEqualTo(event.getBlockchainId());
        assertThat(payload.getValue().getSmartContractId()).isEqualTo(event.getSmartContractId());
        assertThat(payload.getValue().getClauseName()).isEqualTo("recordReading");
        assertThat(payload.getValue().getClauseArguments()).isEqualTo(event.getClauseArguments());
        assertThat(execution.getPayload()).isSameAs(payload.getValue());
        assertThat(execution.getTimestamps()).containsEntry(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED, publishedAt);
        assertThat(execution.getTimestamps().get(SmartContractExecutionEvent.INBOUND_QUEUE_PROCESSED))
                .isEqualTo(execution.getTimestamps().get(SmartContractExecutionEvent.INBOUND_QUEUE_CONSUMED));
        assertThat(OffsetDateTime.parse(execution.getTimestamps().get(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED)))
                .isBetween(before, OffsetDateTime.now(ZoneOffset.UTC));
        verify(publisher, never()).publishOutcome(any());
    }
}
