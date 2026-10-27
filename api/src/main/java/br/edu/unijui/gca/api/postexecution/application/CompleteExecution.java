package br.edu.unijui.gca.api.postexecution.application;

import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionMetadata;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.postexecution.application.ports.WebhookDelivery;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public class CompleteExecution {

    private final ExecutionStore store;
    private final ExecutionMetadata metadata;
    private final WebhookDelivery webhooks;

    public CompleteExecution(ExecutionStore store, ExecutionMetadata metadata, WebhookDelivery webhooks) {
        this.store = store;
        this.metadata = metadata;
        this.webhooks = webhooks;
    }

    public void process(SmartContractExecutionDto event) {
        SmartContractExecution smartContractExecution = store.findById(event.getId());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_CONSUMED, OffsetDateTime.now(ZoneOffset.UTC).toString());
        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());

        UUID smartContractId = smartContractExecution.getPayload().getSmartContractId();

        SmartContract smartContract  = metadata.findSmartContract(smartContractId);

        smartContract
            .getClauses()
            .stream()
            .filter(item -> item.getName().equals(event.getPayload().getClauseName()))
            .findFirst()
            .map(SmartContractClauseDto::getPostExecutionActions)
            .ifPresent(actions -> {
                for (var action: actions) {
                    if (action.getType() == PostExecutionActionType.WEBHOOK) {
                        webhooks.deliver(action.getUrl());
                    }
                }
            });

        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());

        store.update(smartContractExecution);
    }

}
