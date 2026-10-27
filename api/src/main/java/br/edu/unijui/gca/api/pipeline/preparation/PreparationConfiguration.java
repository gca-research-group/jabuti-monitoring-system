package br.edu.unijui.gca.api.pipeline.preparation;

import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.pipeline.preparation.application.PrepareExecution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("preparation")
public class PreparationConfiguration {

    @Bean
    public PrepareExecution prepareExecution(ExecutionStore store, BlockchainStore blockchains, SmartContractStore contracts, ExecutionEventPublisher publisher) {
        return new PrepareExecution(store, blockchains, contracts, publisher);
    }
}
