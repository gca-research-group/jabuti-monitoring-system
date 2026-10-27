package br.edu.unijui.gca.api.execution.adapters.rabbitmq;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.execution.application.ExecuteContract;
import br.edu.unijui.gca.api.execution.application.ExecutionRejectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("execution")
@RequiredArgsConstructor
@Component
public class ContractExecutionListener {

    private final ExecuteContract execution;

    @RabbitListener(id = "execution", queues = {QueueNames.EXECUTION_QUEUE},
            containerFactory = "executionListenerContainerFactory")
    public void process(SmartContractPayloadDto payload) {
        try {
            execution.process(payload);
        } catch (ExecutionRejectedException exception) {
            throw new AmqpRejectAndDontRequeueException(exception.getCause());
        }
    }
}
