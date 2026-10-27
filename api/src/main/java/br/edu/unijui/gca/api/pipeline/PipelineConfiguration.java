package br.edu.unijui.gca.api.pipeline;

import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.pipeline.adapters.rabbitmq.RabbitMQExecutionEventPublisher;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"preparation", "execution", "post-execution"})
public class PipelineConfiguration {

    @Bean
    public ExecutionEventPublisher executionEventPublisher(AmqpTemplate amqpTemplate, SmartContractExecutionMapper mapper) {
        return new RabbitMQExecutionEventPublisher(amqpTemplate, mapper);
    }
}
