package br.edu.unijui.gca.api.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }


    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(QueueNames.MAIN_EXCHANGE);
    }

    @Bean
    public DirectExchange inboundDlx() {
        return new DirectExchange(QueueNames.INBOUND_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue inboundDlq() {
        return QueueBuilder
                .durable(QueueNames.INBOUND_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding inboundDlqBinding() {
        return BindingBuilder
                .bind(inboundDlq())
                .to(inboundDlx())
                .with(QueueNames.INBOUND_DEAD_LETTER_QUEUE_ROUTING_KEY);
    }

    @Bean
    public Queue inbound() {
        return QueueBuilder
                .durable(QueueNames.INBOUND_QUEUE)
                .deadLetterExchange(QueueNames.INBOUND_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QueueNames.INBOUND_DEAD_LETTER_QUEUE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding inboundBinding() {
        return BindingBuilder
                .bind(inbound())
                .to(exchange())
                .with(QueueNames.INBOUND_ROUTING_KEY);
    }

    @Bean
    public DirectExchange executionDlx() {
        return new DirectExchange(QueueNames.EXECUTION_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue executionDlq() {
        return QueueBuilder
                .durable(QueueNames.EXECUTION_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding executionDlqBinding() {
        return BindingBuilder
                .bind(executionDlq())
                .to(executionDlx())
                .with(QueueNames.EXECUTION_DEAD_LETTER_QUEUE_ROUTING_KEY);
    }

    @Bean
    public Queue execution() {
        return QueueBuilder
                .durable(QueueNames.EXECUTION_QUEUE)
                .deadLetterExchange(QueueNames.EXECUTION_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QueueNames.EXECUTION_DEAD_LETTER_QUEUE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding executionBinding() {
        return BindingBuilder
                .bind(execution())
                .to(exchange())
                .with(QueueNames.EXECUTION_ROUTING_KEY);
    }

    @Bean
    public DirectExchange outboundDlx() {
        return new DirectExchange(QueueNames.OUTBOUND_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue outboundDlq() {
        return QueueBuilder
                .durable(QueueNames.OUTBOUND_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding outboundDlqBinding() {
        return BindingBuilder
                .bind(outboundDlq())
                .to(outboundDlx())
                .with(QueueNames.OUTBOUND_DEAD_LETTER_QUEUE_ROUTING_KEY);
    }

    @Bean
    public Queue outbound() {
        return QueueBuilder
                .durable(QueueNames.OUTBOUND_QUEUE)
                .deadLetterExchange(QueueNames.OUTBOUND_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QueueNames.OUTBOUND_DEAD_LETTER_QUEUE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding outboundBinding() {
        return BindingBuilder
                .bind(outbound())
                .to(exchange())
                .with(QueueNames.OUTBOUND_ROUTING_KEY);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
