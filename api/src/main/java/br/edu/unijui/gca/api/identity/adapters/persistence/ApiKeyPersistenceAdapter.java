package br.edu.unijui.gca.api.identity.adapters.persistence;

import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.identity.application.ports.ApiKeyStore;
import br.edu.unijui.gca.api.repositories.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;

@RequiredArgsConstructor
public class ApiKeyPersistenceAdapter implements ApiKeyStore {
    private final ApiKeyRepository repository;

    @Override
    public void save(ApiKey apiKey) {
        repository.save(apiKey);
    }

    @Override
    @Cacheable(cacheNames = "apiKeys", key = "#keyPrefix", sync = true)
    public ApiKey findByKeyPrefix(String keyPrefix) {
        return repository.findByKeyPrefix(keyPrefix).orElseThrow(ResourceNotFoundException::new);
    }
}
