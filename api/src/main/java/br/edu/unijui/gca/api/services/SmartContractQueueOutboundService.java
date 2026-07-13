package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Component
public class SmartContractQueueOutboundService {

    private final SmartContractService smartContractService;

    private final SmartContractExecutionService smartContractExecutionService;

    @RabbitListener(queues = {QueueNames.OUTBOUND_QUEUE})
    public void process(SmartContractExecutionDto event) {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(event.getId());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_CONSUMED, OffsetDateTime.now(ZoneOffset.UTC).toString());
        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());

        UUID smartContractId = smartContractExecution.getPayload().getSmartContractId();

        SmartContract smartContract  = smartContractService.findById(smartContractId);

        smartContract
            .getClauses()
            .stream()
            .filter(item -> item.getName().equals(event.getPayload().getClauseName()))
            .findFirst()
            .map(SmartContractClauseDto::getPostExecutionActions)
            .ifPresent(actions -> {
                for (var action: actions) {
                    if (action.getType() == PostExecutionActionType.WEBHOOK) {
                        postToWebhook(action.getUrl());
                    }
                }
            });

        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());

        smartContractExecutionService.update(smartContractExecution);
    }

    @Async
    private void postToWebhook(String url) {
        try(HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                    {
                      "message": "Hello, World!"
                    }
                    """))
                    .build();

            client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }


    }
}
