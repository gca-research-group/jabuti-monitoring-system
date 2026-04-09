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
        return new DirectExchange("smart-contract-exchange");
    }

    @Bean
    public DirectExchange inboundDlx() {
        return new DirectExchange("smart-contract-inbound.dlx");
    }

    @Bean
    public Queue inboundDlq() {
        return QueueBuilder
                .durable("smart-contract-inbound.dlq")
                .build();
    }

    @Bean
    public Binding inboundDlqBinding() {
        return BindingBuilder
                .bind(inboundDlq())
                .to(inboundDlx())
                .with("smart-contract-inbound.dlq-routing-key");
    }

    @Bean
    public Queue inbound() {
        return QueueBuilder
                .durable("smart-contract-inbound-queue")
                .deadLetterExchange("smart-contract-inbound.dlx")
                .deadLetterRoutingKey("smart-contract-inbound.dlq-routing-key")
                .build();
    }

    @Bean
    public Binding inboundBinding() {
        return BindingBuilder
                .bind(inbound())
                .to(exchange())
                .with("smart-contract-inbound-routing-key");
    }

    @Bean
    public DirectExchange executionDlx() {
        return new DirectExchange("smart-contract-execution.dlx");
    }

    @Bean
    public Queue executionDlq() {
        return QueueBuilder
                .durable("smart-contract-execution.dlq")
                .build();
    }

    @Bean
    public Binding executionDlqBinding() {
        return BindingBuilder
                .bind(executionDlq())
                .to(executionDlx())
                .with("smart-contract-execution.dlq-routing-key");
    }

    @Bean
    public Queue execution() {
        return QueueBuilder
                .durable("smart-contract-execution-queue")
                .deadLetterExchange("smart-contract-execution.dlx")
                .deadLetterRoutingKey("smart-contract-execution.dlq-routing-key")
                .build();
    }

    @Bean
    public Binding executionBinding() {
        return BindingBuilder
                .bind(execution())
                .to(exchange())
                .with("smart-contract-execution-routing-key");
    }


    @Bean
    public DirectExchange outboundDlx() {
        return new DirectExchange("smart-contract-outbound.dlx");
    }

    @Bean
    public Queue outboundDlq() {
        return QueueBuilder
                .durable("smart-contract-outbound.dlq")
                .build();
    }

    @Bean
    public Binding outboundDlqBinding() {
        return BindingBuilder
                .bind(outboundDlq())
                .to(outboundDlx())
                .with("smart-contract-outbound.dlq-routing-key");
    }

    @Bean
    public Queue outbound() {
        return QueueBuilder
                .durable("smart-contract-outbound-queue")
                .deadLetterExchange("smart-contract-outbound.dlx")
                .deadLetterRoutingKey("smart-contract-outbound.dlq-routing-key")
                .build();
    }

    @Bean
    public Binding outboundBinding() {
        return BindingBuilder
                .bind(outbound())
                .to(exchange())
                .with("smart-contract-outbound-routing-key");
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
