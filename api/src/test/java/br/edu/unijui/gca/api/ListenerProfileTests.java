package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.config.RabbitMQConfig;
import br.edu.unijui.gca.api.execution.adapters.blockchain.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.execution.ExecutionConfiguration;
import br.edu.unijui.gca.api.execution.adapters.rabbitmq.ContractExecutionListener;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.pipeline.PipelineConfiguration;
import br.edu.unijui.gca.api.postexecution.PostExecutionConfiguration;
import br.edu.unijui.gca.api.postexecution.adapters.rabbitmq.PostExecutionListener;
import br.edu.unijui.gca.api.preparation.PreparationConfiguration;
import br.edu.unijui.gca.api.preparation.adapters.rabbitmq.ExecutionPreparationListener;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ListenerProfileTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(Listeners.class)
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(AmqpTemplate.class, () -> mock(AmqpTemplate.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(BlockchainConnectionFactory.class, () -> mock(BlockchainConnectionFactory.class))
            .withBean(ExecutionStore.class, () -> mock(ExecutionStore.class))
            .withBean(SmartContractExecutionMapper.class, () -> mock(SmartContractExecutionMapper.class))
            .withBean(SmartContractStore.class, () -> mock(SmartContractStore.class))
            .withBean(BlockchainStore.class, () -> mock(BlockchainStore.class))
            // Inspect real listener containers without starting them or contacting a broker.
            .withBean("lifecycleProcessor", LifecycleProcessor.class, () -> mock(LifecycleProcessor.class));

    @ParameterizedTest
    @CsvSource({"preparation,smart-contract.inbound.queue", "execution,smart-contract.execution.queue",
            "post-execution,smart-contract.outbound.queue"})
    void workerRegistersOnlyItsOwnQueue(String role, String queue) {
        runner.withPropertyValues("spring.profiles.active=" + role).run(context -> {
            assertThat(context).hasNotFailed();
            var containers = context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers();
            assertThat(containers).singleElement().satisfies(container -> {
                var listener = (SimpleMessageListenerContainer) container;
                assertThat(listener.getQueueNames()).containsExactly(queue);
                assertThat(listener.isAutoStartup()).isTrue();
                assertThat(listener.isRunning()).isFalse();
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"intake", "metadata-management", "intake,metadata-management"})
    void webRolesDoNotRegisterQueueListeners(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RabbitListenerEndpointRegistry.class).getListenerContainers()).isEmpty();
        });
    }

    @Test
    void combinedWorkersUseIndependentEnvironmentSettings() {
        runner.withPropertyValues("spring.profiles.active=intake,preparation,execution,post-execution,metadata-management",
                "PREPARATION_CONCURRENCY=2", "PREPARATION_MAX_CONCURRENCY=3", "PREPARATION_PREFETCH=4",
                "EXECUTION_CONCURRENCY=5", "EXECUTION_MAX_CONCURRENCY=6", "EXECUTION_PREFETCH=7",
                "POST_EXECUTION_CONCURRENCY=8", "POST_EXECUTION_MAX_CONCURRENCY=9", "POST_EXECUTION_PREFETCH=10")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var registry = context.getBean(RabbitListenerEndpointRegistry.class);
                    assertThat(registry.getListenerContainers()).hasSize(3);
                    Map<String, String> queues = Map.of("preparation", QueueNames.INBOUND_QUEUE,
                            "execution", QueueNames.EXECUTION_QUEUE, "post-execution", QueueNames.OUTBOUND_QUEUE);
                    Map<String, Integer> concurrency = Map.of("preparation", 2, "execution", 5, "post-execution", 8);
                    queues.forEach((role, queue) -> {
                        var listener = (SimpleMessageListenerContainer) registry.getListenerContainer(role);
                        int configuredConcurrency = concurrency.get(role);
                        assertThat(listener.getQueueNames()).containsExactly(queue);
                        // Spring AMQP exposes setters but no public getters for these container settings.
                        assertThat(ReflectionTestUtils.getField(listener, "concurrentConsumers")).isEqualTo(configuredConcurrency);
                        assertThat(ReflectionTestUtils.getField(listener, "maxConcurrentConsumers")).isEqualTo(configuredConcurrency + 1);
                        assertThat(ReflectionTestUtils.getField(listener, "prefetchCount")).isEqualTo(configuredConcurrency + 2);
                        assertThat(listener.isAutoStartup()).isTrue();
                        assertThat(listener.isRunning()).isFalse();
                    });
                });
    }

    @EnableRabbit
    @Import({RabbitMQConfig.class, PipelineConfiguration.class, PreparationConfiguration.class, ExecutionPreparationListener.class,
            ExecutionConfiguration.class, ContractExecutionListener.class, PostExecutionConfiguration.class, PostExecutionListener.class})
    static class Listeners {
    }
}
