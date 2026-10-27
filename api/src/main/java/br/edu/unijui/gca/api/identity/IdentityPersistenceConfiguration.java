package br.edu.unijui.gca.api.identity;

import br.edu.unijui.gca.api.identity.adapters.persistence.ApiKeyPersistenceAdapter;
import br.edu.unijui.gca.api.identity.adapters.persistence.UserPersistenceAdapter;
import br.edu.unijui.gca.api.identity.application.ports.ApiKeyStore;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;
import br.edu.unijui.gca.api.repositories.ApiKeyRepository;
import br.edu.unijui.gca.api.repositories.UserRepository;
import br.edu.unijui.gca.api.specifications.UserSpecification;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"intake", "metadata-management"})
public class IdentityPersistenceConfiguration {
    @Bean
    public UserStore userStore(UserRepository repository, UserSpecification specification) {
        return new UserPersistenceAdapter(repository, specification);
    }

    @Bean
    public ApiKeyStore apiKeyStore(ApiKeyRepository repository) {
        return new ApiKeyPersistenceAdapter(repository);
    }
}
