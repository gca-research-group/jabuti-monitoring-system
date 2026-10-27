package br.edu.unijui.gca.api.pipeline.adapters.rabbitmq;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import org.springframework.amqp.core.AmqpTemplate;

public class RabbitMQExecutionEventPublisher implements ExecutionEventPublisher {

    private final AmqpTemplate amqpTemplate;
    private final SmartContractExecutionMapper mapper;

    public RabbitMQExecutionEventPublisher(AmqpTemplate amqpTemplate, SmartContractExecutionMapper mapper) {
        this.amqpTemplate = amqpTemplate;
        this.mapper = mapper;
    }

    @Override
    public void publishExecution(SmartContractPayloadDto payload) {
        amqpTemplate.convertAndSend(QueueNames.MAIN_EXCHANGE, QueueNames.EXECUTION_ROUTING_KEY, payload);
    }

    @Override
    public void publishOutcome(SmartContractExecution execution) {
        amqpTemplate.convertAndSend(QueueNames.MAIN_EXCHANGE, QueueNames.OUTBOUND_ROUTING_KEY, mapper.toDto(execution));
    }
}
