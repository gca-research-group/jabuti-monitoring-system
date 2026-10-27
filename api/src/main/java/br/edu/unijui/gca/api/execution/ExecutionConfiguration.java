package br.edu.unijui.gca.api.execution;

import br.edu.unijui.gca.api.execution.adapters.blockchain.BlockchainContractInvoker;
import br.edu.unijui.gca.api.execution.application.ExecuteContract;
import br.edu.unijui.gca.api.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.execution.adapters.blockchain.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionMetadata;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Profile("execution")
public class ExecutionConfiguration {

    @Bean
    public ContractInvoker contractInvoker(BlockchainConnectionFactory factory, ObjectMapper objectMapper,
                                           ExecutionMetadata metadata) {
        return new BlockchainContractInvoker(factory, objectMapper, metadata);
    }

    @Bean
    public ExecuteContract executeContract(ExecutionStore store, ExecutionMetadata metadata,
                                           ContractInvoker invoker, ExecutionEventPublisher publisher) {
        return new ExecuteContract(store, metadata, invoker, publisher);
    }
}
