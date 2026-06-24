package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Slf4j
@Component
public class SmartContractQueueOutboundService {

    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @RabbitListener(queues = {QueueNames.OUTBOUND_QUEUE})
    public void process(SmartContractExecutionDto event) {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(event.getId());

        Map<String, String> timestamps = smartContractExecution.getTimestamps();

        timestamps.put("outbound_queue.consumed", OffsetDateTime.now(ZoneOffset.UTC).toString());
        timestamps.put("outbound_queue.processing", OffsetDateTime.now(ZoneOffset.UTC).toString());
        timestamps.put("outbound_queue.processed", OffsetDateTime.now(ZoneOffset.UTC).toString());

        smartContractExecution.setTimestamps(timestamps);
        smartContractExecutionService.update(smartContractExecution);
    }
}
