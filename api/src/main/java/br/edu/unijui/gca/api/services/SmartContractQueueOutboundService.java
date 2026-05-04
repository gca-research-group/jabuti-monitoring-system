package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class SmartContractQueueOutboundService {
    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @RabbitListener(queues = {QueueNames.OUTBOUND_QUEUE})
    public void process(SmartContractExecutionDto item) {
        item.setOutboundQueueConsumedAt(Instant.now());
        item.setOutboundQueueProcessingStartedAt(Instant.now());
        item.setOutboundQueueProcessedAt(Instant.now());
        smartContractExecutionService.update(item.getId(), item);
    }
}
