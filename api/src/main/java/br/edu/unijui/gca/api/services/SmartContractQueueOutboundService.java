package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Component
public class SmartContractQueueOutboundService {

    private final SmartContractExecutionService smartContractExecutionService;

    @RabbitListener(queues = {QueueNames.OUTBOUND_QUEUE})
    public void process(SmartContractExecutionDto event) {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(event.getId());

        Map<SmartContractExecutionEvent, String> timestamps = smartContractExecution.getTimestamps();

        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_CONSUMED, OffsetDateTime.now(ZoneOffset.UTC).toString());
        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSING, OffsetDateTime.now(ZoneOffset.UTC).toString());
        timestamps.put(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED, OffsetDateTime.now(ZoneOffset.UTC).toString());

        smartContractExecutionService.update(smartContractExecution);
    }
}
