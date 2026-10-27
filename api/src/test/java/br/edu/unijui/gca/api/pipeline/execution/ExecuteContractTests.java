package br.edu.unijui.gca.api.pipeline.execution;

import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.pipeline.execution.application.ExecuteContract;
import br.edu.unijui.gca.api.pipeline.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExecuteContractTests {

    @Test
    void successfulInvocationStoresResultAndPublishesTheExistingExecutionOutcome() {
        var store = mock(ExecutionStore.class);
        var blockchains = mock(BlockchainStore.class);
        var invoker = mock(ContractInvoker.class);
        var publisher = mock(ExecutionEventPublisher.class);
        var payload = SmartContractPayloadDto.builder().id(UUID.randomUUID())
                .blockchainId(UUID.randomUUID()).smartContractId(UUID.randomUUID())
                .clauseName("recordReading").clauseArguments(List.of(new SmartContractClauseArgumentDto("reading", "42"))).build();
        var execution = new SmartContractExecution();
        execution.setId(payload.getId());
        execution.setPayload(payload);
        execution.setStatus(SmartContractExecutionStatus.PROCESSING);
        String preparedAt = "2026-10-27T10:00:00Z";
        execution.setTimestamps(new HashMap<>(Map.of(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED, preparedAt)));
        var blockchain = new Blockchain();
        blockchain.setId(payload.getBlockchainId());
        var savedStates = new ArrayList<SmartContractExecutionStatus>();
        when(store.findById(payload.getId())).thenReturn(execution);
        when(blockchains.findById(payload.getBlockchainId())).thenReturn(blockchain);
        when(invoker.invoke(blockchain, payload)).thenReturn("receipt-42");
        doAnswer(call -> { savedStates.add(execution.getStatus()); return null; }).when(store).update(execution);
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        new ExecuteContract(store, blockchains, invoker, publisher).process(payload);

        assertThat(savedStates).containsExactly(SmartContractExecutionStatus.PROCESSING, SmartContractExecutionStatus.PROCESSED);
        assertThat(execution.getResult()).isEqualTo("receipt-42");
        assertThat(execution.getPayload()).isSameAs(payload);
        assertThat(execution.getTimestamps()).containsEntry(SmartContractExecutionEvent.EXECUTION_QUEUE_PUBLISHED, preparedAt);
        for (var timestamp : List.of(SmartContractExecutionEvent.EXECUTION_QUEUE_CONSUMED,
                SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSING, SmartContractExecutionEvent.EXECUTION_QUEUE_PROCESSED,
                SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED)) {
            var value = OffsetDateTime.parse(execution.getTimestamps().get(timestamp));
            assertThat(value).isBetween(before, OffsetDateTime.now(ZoneOffset.UTC));
            assertThat(value.getOffset()).isEqualTo(ZoneOffset.UTC);
        }
        verify(invoker).invoke(blockchain, payload);
        verify(publisher).publishOutcome(execution);
        verify(publisher, never()).publishExecution(any());
    }
}
