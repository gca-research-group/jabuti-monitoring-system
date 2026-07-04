package br.edu.unijui.gca.api.repositories;

import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.interfaces.IRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository  extends IRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);
}
