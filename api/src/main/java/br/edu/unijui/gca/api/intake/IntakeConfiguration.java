package br.edu.unijui.gca.api.intake;

import br.edu.unijui.gca.api.intake.adapters.rabbitmq.RabbitMQInboundEventPublisher;
import br.edu.unijui.gca.api.intake.application.AcceptExecution;
import br.edu.unijui.gca.api.intake.application.ports.InboundEventPublisher;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("intake")
public class IntakeConfiguration {

    @Bean
    public InboundEventPublisher inboundEventPublisher(AmqpTemplate amqpTemplate) {
        return new RabbitMQInboundEventPublisher(amqpTemplate);
    }

    @Bean
    public AcceptExecution acceptExecution(InboundEventPublisher publisher) {
        return new AcceptExecution(publisher);
    }
}
