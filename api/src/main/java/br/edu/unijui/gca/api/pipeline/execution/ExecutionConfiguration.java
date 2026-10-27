package br.edu.unijui.gca.api.pipeline.execution;

import br.edu.unijui.gca.api.pipeline.execution.adapters.blockchain.BlockchainContractInvoker;
import br.edu.unijui.gca.api.pipeline.execution.application.ExecuteContract;
import br.edu.unijui.gca.api.pipeline.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.pipeline.execution.adapters.blockchain.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
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
                                           SmartContractStore contracts) {
        return new BlockchainContractInvoker(factory, objectMapper, contracts);
    }

    @Bean
    public ExecuteContract executeContract(ExecutionStore store, BlockchainStore blockchains,
                                           ContractInvoker invoker, ExecutionEventPublisher publisher) {
        return new ExecuteContract(store, blockchains, invoker, publisher);
    }
}
