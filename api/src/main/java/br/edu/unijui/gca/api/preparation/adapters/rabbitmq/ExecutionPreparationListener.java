package br.edu.unijui.gca.api.preparation.adapters.rabbitmq;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.preparation.application.PreparationRejectedException;
import br.edu.unijui.gca.api.preparation.application.PrepareExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("preparation")
@RequiredArgsConstructor
@Component
public class ExecutionPreparationListener {

    private final PrepareExecution preparation;

    @RabbitListener(id = "preparation", queues = {QueueNames.INBOUND_QUEUE},
            containerFactory = "preparationListenerContainerFactory")
    public void process(SmartContractQueueInboundEventDto event) {
        try {
            preparation.process(event);
        } catch (PreparationRejectedException exception) {
            throw new AmqpRejectAndDontRequeueException(exception.getCause());
        }
    }
}
