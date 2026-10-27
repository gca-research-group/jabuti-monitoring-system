package br.edu.unijui.gca.api.pipeline.postexecution;

import br.edu.unijui.gca.api.dtos.PostExecutionActionDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.pipeline.postexecution.application.CompleteExecution;
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

class CompleteExecutionTests {

    @Test
    void configuredWebhooksAreDeliveredInOrderBeforeCompletionIsStored() {
        var store = mock(ExecutionStore.class);
        var contracts = mock(SmartContractStore.class);
        UUID contractId = UUID.randomUUID();
        var execution = new SmartContractExecution();
        execution.setId(UUID.randomUUID());
        execution.setPayload(SmartContractPayloadDto.builder().smartContractId(contractId).clauseName("storedClause").build());
        execution.setStatus(SmartContractExecutionStatus.PROCESSED);
        String publishedAt = "2026-10-27T10:00:00Z";
        execution.setTimestamps(new HashMap<>(Map.of(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED, publishedAt)));
        var event = SmartContractExecutionDto.builder().id(execution.getId())
                .payload(SmartContractPayloadDto.builder().smartContractId(UUID.randomUUID()).clauseName("recordReading").build()).build();
        var contract = new SmartContract();
        var first = PostExecutionActionDto.builder().type(PostExecutionActionType.WEBHOOK).url("https://example.test/first").build();
        var second = PostExecutionActionDto.builder().type(PostExecutionActionType.WEBHOOK).url("https://example.test/second").build();
        var ignored = PostExecutionActionDto.builder().type(PostExecutionActionType.EVENT).url("https://example.test/ignored").build();
        contract.setClauses(List.of(new SmartContractClauseDto("other", List.of(), List.of(second)),
                new SmartContractClauseDto("recordReading", List.of(), List.of(first, ignored, second)),
                new SmartContractClauseDto("recordReading", List.of(), List.of(first))));
        when(store.findById(event.getId())).thenReturn(execution);
        when(contracts.findById(contractId)).thenReturn(contract);
        var effects = new ArrayList<String>();
        doAnswer(call -> { effects.add("saved"); return null; }).when(store).update(execution);
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        new CompleteExecution(store, contracts, effects::add).process(event);

        assertThat(effects).containsExactly("https://example.test/first", "https://example.test/second", "saved");
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.PROCESSED);
        assertThat(execution.getTimestamps()).containsEntry(SmartContractExecutionEvent.OUTBOUND_QUEUE_PUBLISHED, publishedAt);
        for (var timestamp : List.of(SmartContractExecutionEvent.OUTBOUND_QUEUE_CONSUMED,
                SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSING, SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED)) {
            var value = OffsetDateTime.parse(execution.getTimestamps().get(timestamp));
            assertThat(value).isBetween(before, OffsetDateTime.now(ZoneOffset.UTC));
            assertThat(value.getOffset()).isEqualTo(ZoneOffset.UTC);
        }
    }
}
