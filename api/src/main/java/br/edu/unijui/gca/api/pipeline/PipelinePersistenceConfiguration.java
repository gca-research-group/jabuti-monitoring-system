package br.edu.unijui.gca.api.pipeline;

import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.pipeline.adapters.persistence.ExecutionPersistenceAdapter;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionManagementStore;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"metadata-management", "preparation", "execution", "post-execution"})
public class PipelinePersistenceConfiguration {
    @Bean
    public ExecutionManagementStore executionStore(SmartContractExecutionRepository repository,
            SmartContractExecutionSpecification specification, SmartContractExecutionMapper mapper) {
        return new ExecutionPersistenceAdapter(repository, specification, mapper);
    }
}
