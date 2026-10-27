package br.edu.unijui.gca.api.identity.application.ports;

import br.edu.unijui.gca.api.entities.ApiKey;

public interface ApiKeyStore {
    ApiKey findByKeyPrefix(String prefix);
    void save(ApiKey apiKey);
}
