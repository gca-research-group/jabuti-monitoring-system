package br.edu.unijui.gca.api.pipeline.postexecution.adapters.rabbitmq;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.pipeline.postexecution.application.CompleteExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("post-execution")
@RequiredArgsConstructor
@Component
public class PostExecutionListener {

    private final CompleteExecution postExecution;

    @RabbitListener(id = "post-execution", queues = {QueueNames.OUTBOUND_QUEUE},
            containerFactory = "postExecutionListenerContainerFactory")
    public void process(SmartContractExecutionDto event) {
        postExecution.process(event);
    }
}
