package br.edu.unijui.gca.api.intake.adapters.rabbitmq;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.intake.application.ports.InboundEventPublisher;
import org.springframework.amqp.core.AmqpTemplate;

public class RabbitMQInboundEventPublisher implements InboundEventPublisher {

    private final AmqpTemplate amqpTemplate;

    public RabbitMQInboundEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    @Override
    public void publish(SmartContractQueueInboundEventDto event) {
        amqpTemplate.convertAndSend(QueueNames.MAIN_EXCHANGE, QueueNames.INBOUND_ROUTING_KEY, event);
    }
}
