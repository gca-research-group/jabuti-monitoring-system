package br.edu.unijui.gca.api.preparation;

import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionEventPublisher;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionMetadata;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.preparation.application.PrepareExecution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("preparation")
public class PreparationConfiguration {

    @Bean
    public PrepareExecution prepareExecution(ExecutionStore store, ExecutionMetadata metadata, ExecutionEventPublisher publisher) {
        return new PrepareExecution(store, metadata, publisher);
    }
}
