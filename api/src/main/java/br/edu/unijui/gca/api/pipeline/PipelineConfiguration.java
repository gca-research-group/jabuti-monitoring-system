package br.edu.unijui.gca.api.pipeline;

import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.pipeline.adapters.persistence.ExecutionPersistenceAdapter;
import br.edu.unijui.gca.api.pipeline.adapters.persistence.MetadataPersistenceAdapter;
import br.edu.unijui.gca.api.pipeline.adapters.rabbitmq.RabbitMQExecutionEventPublisher;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionMetadata;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.services.BlockchainService;
import br.edu.unijui.gca.api.services.SmartContractExecutionService;
import br.edu.unijui.gca.api.services.SmartContractService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"preparation", "execution", "post-execution"})
public class PipelineConfiguration {

    @Bean
    public ExecutionStore executionStore(SmartContractExecutionService service) {
        return new ExecutionPersistenceAdapter(service);
    }

    @Bean
    public ExecutionMetadata executionMetadata(BlockchainService blockchains, SmartContractService contracts) {
        return new MetadataPersistenceAdapter(blockchains, contracts);
    }

    @Bean
    public ExecutionEventPublisher executionEventPublisher(AmqpTemplate amqpTemplate, SmartContractExecutionMapper mapper) {
        return new RabbitMQExecutionEventPublisher(amqpTemplate, mapper);
    }
}
