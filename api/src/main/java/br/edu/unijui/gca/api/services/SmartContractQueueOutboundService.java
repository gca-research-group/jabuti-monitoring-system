package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionEventDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class SmartContractQueueOutboundService {
    @Autowired
    private SmartContractExecutionEventService smartContractExecutionEventService;

    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @RabbitListener(queues = {QueueNames.OUTBOUND_QUEUE})
    public void process(SmartContractExecutionDto event) {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(event.getId());

        smartContractExecutionEventService.create(smartContractExecution, "outbound_queue.consumed", Instant.now());
        smartContractExecutionEventService.create(smartContractExecution, "outbound_queue.processing", Instant.now());

        smartContractExecutionService.update(event.getId(), event);

        smartContractExecutionEventService.create(smartContractExecution, "outbound_queue.processed", Instant.now());
    }
}
