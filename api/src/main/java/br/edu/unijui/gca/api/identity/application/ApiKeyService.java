package br.edu.unijui.gca.api.identity.application;

import br.edu.unijui.gca.api.identity.application.ports.ApiKeyStore;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;

import br.edu.unijui.gca.api.identity.adapters.http.dto.CreateApiKeyResponse;
import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.ApiKeyNotFoundException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;

import br.edu.unijui.gca.api.utils.HashUtils;
import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@RequiredArgsConstructor
@Profile({"intake", "metadata-management"})
@Service
public class ApiKeyService {
    private final ApiKeyStore repository;

    private final UserStore userService;

    private final SecureRandom secureRandom = new SecureRandom();

    public CreateApiKeyResponse create(Long userId) {

        User user = userService.findById(userId);

        String prefix = generatePrefix();
        String secret = generateSecret();

        String rawApiKey = buildRawKey(prefix, secret);
        byte[] hash = HashUtils.sha256(secret);

        ApiKey apiKey = new ApiKey();

        apiKey.setUser(user);
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(hash);
        apiKey.setStatus(true);

        repository.save(apiKey);

        return new CreateApiKeyResponse(userId, rawApiKey);
    }

    private String generatePrefix() {
        byte[] bytes = new byte[6];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String buildRawKey(String prefix, String secret) {
        return "jms_" + prefix + "." + secret;
    }

    public ApiKey findByKeyPrefix(String keyPrefix) {
        return repository.findByKeyPrefix(keyPrefix);
    }

    public void validateApiKey(String secret, byte[] keyHash) {
        byte[] incomingHash = HashUtils.sha256(secret);

        if (!MessageDigest.isEqual(incomingHash, keyHash)) {
            throw new ApiKeyNotFoundException();
        }
    }
}
