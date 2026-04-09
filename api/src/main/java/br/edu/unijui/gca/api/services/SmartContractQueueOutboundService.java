package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmartContractQueueOutboundService {
    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @RabbitListener(queues = {"smart-contract-outbound-queue"})
    public void process(SmartContractExecutionDto item) {
        smartContractExecutionService.update(item.getId(), item);
    }
}
