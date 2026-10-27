package br.edu.unijui.gca.api.metadata;

import br.edu.unijui.gca.api.metadata.adapters.persistence.BlockchainPersistenceAdapter;
import br.edu.unijui.gca.api.metadata.adapters.persistence.SmartContractPersistenceAdapter;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.metadata.application.ports.ExecutionManagementStore;
import br.edu.unijui.gca.api.pipeline.adapters.persistence.ExecutionPersistenceAdapter;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.BlockchainRepository;
import br.edu.unijui.gca.api.repositories.SmartContractRepository;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.BlockchainSpecification;
import br.edu.unijui.gca.api.specifications.SmartContractSpecification;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"metadata-management", "preparation", "execution", "post-execution"})
public class MetadataPersistenceConfiguration {
    @Bean
    public BlockchainStore blockchainStore(BlockchainRepository repository, BlockchainSpecification specification) {
        return new BlockchainPersistenceAdapter(repository, specification);
    }

    @Bean
    public SmartContractStore smartContractStore(SmartContractRepository repository, SmartContractSpecification specification) {
        return new SmartContractPersistenceAdapter(repository, specification);
    }

    @Bean
    public ExecutionManagementStore executionStore(SmartContractExecutionRepository repository,
            SmartContractExecutionSpecification specification, SmartContractExecutionMapper mapper) {
        return new ExecutionPersistenceAdapter(repository, specification, mapper);
    }
}
