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
import br.edu.unijui.gca.api.pipeline.postexecution.application.ports.WebhookDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PostExecutionFailureTests {

    private final ExecutionStore store = mock(ExecutionStore.class);
    private final SmartContractStore contracts = mock(SmartContractStore.class);
    private final WebhookDelivery delivery = mock(WebhookDelivery.class);
    private final CompleteExecution application = new CompleteExecution(store, contracts, delivery);
    private final SmartContractExecution execution = new SmartContractExecution();
    private final SmartContract contract = new SmartContract();
    private SmartContractExecutionDto event;

    @BeforeEach
    void existingExecutionWithWebhooks() {
        var payload = SmartContractPayloadDto.builder().smartContractId(UUID.randomUUID()).clauseName("recordReading").build();
        execution.setId(UUID.randomUUID());
        execution.setPayload(payload);
        execution.setStatus(SmartContractExecutionStatus.FAILED);
        execution.setTimestamps(new HashMap<>());
        event = SmartContractExecutionDto.builder().id(execution.getId()).payload(payload).build();
        contract.setClauses(List.of(new SmartContractClauseDto("recordReading", List.of(), List.of(
                PostExecutionActionDto.builder().type(PostExecutionActionType.WEBHOOK).url("https://example.test/first").build(),
                PostExecutionActionDto.builder().type(PostExecutionActionType.WEBHOOK).url("https://example.test/second").build()))));
        when(store.findById(event.getId())).thenReturn(execution);
        when(contracts.findById(payload.getSmartContractId())).thenReturn(contract);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-clause", "null-actions", "empty-actions", "event-action"})
    void noWebhookActionStillStoresCompletionWithoutChangingExecutionStatus(String scenario) {
        switch (scenario) {
            case "missing-clause" -> contract.setClauses(List.of());
            case "null-actions" -> contract.setClauses(List.of(new SmartContractClauseDto("recordReading", List.of(), null)));
            case "empty-actions" -> contract.setClauses(List.of(new SmartContractClauseDto("recordReading", List.of(), List.of())));
            default -> contract.setClauses(List.of(new SmartContractClauseDto("recordReading", List.of(),
                    List.of(PostExecutionActionDto.builder().type(PostExecutionActionType.EVENT).build()))));
        }

        application.process(event);

        verifyNoInteractions(delivery);
        verify(store).update(execution);
        assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
        assertThat(execution.getTimestamps()).containsKey(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED);
    }

    @Test
    void deliveryFailureStopsLaterActionsAndEscapesWithoutStoringCompletion() {
        var failure = new IllegalStateException("delivery failed");
        doThrow(failure).when(delivery).deliver("https://example.test/first");

        assertThatThrownBy(() -> application.process(event)).isSameAs(failure);
        verify(delivery).deliver("https://example.test/first");
        verify(delivery, never()).deliver("https://example.test/second");
        verify(store, never()).update(any());
        assertThat(execution.getTimestamps()).containsKeys(SmartContractExecutionEvent.OUTBOUND_QUEUE_CONSUMED,
                        SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSING)
                .doesNotContainKey(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED);
    }

    @Test
    void lookupFailureEscapesBeforeMetadataOrDelivery() {
        var failure = new IllegalStateException("execution unavailable");
        when(store.findById(event.getId())).thenThrow(failure);

        assertThatThrownBy(() -> application.process(event)).isSameAs(failure);
        verifyNoInteractions(contracts, delivery);
    }

    @Test
    void metadataFailureEscapesWithoutDeliveryOrCompletion() {
        var failure = new IllegalStateException("contract unavailable");
        when(contracts.findById(execution.getPayload().getSmartContractId())).thenThrow(failure);

        assertThatThrownBy(() -> application.process(event)).isSameAs(failure);
        verifyNoInteractions(delivery);
        verify(store, never()).update(any());
    }

    @Test
    void persistenceFailureAfterDeliveryPropagatesWithoutRepeatingDelivery() {
        var failure = new IllegalStateException("completion not saved");
        doThrow(failure).when(store).update(execution);

        assertThatThrownBy(() -> application.process(event)).isSameAs(failure);
        verify(delivery).deliver("https://example.test/first");
        verify(delivery).deliver("https://example.test/second");
        assertThat(execution.getTimestamps()).containsKey(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED);
    }
}
