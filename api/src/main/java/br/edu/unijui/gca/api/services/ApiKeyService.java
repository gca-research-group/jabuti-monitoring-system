package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.apikey.ApiKeyDto;
import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.repositories.ApiKeyRepository;
import br.edu.unijui.gca.api.utils.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class ApiKeyService {
    private final ApiKeyRepository repository;

    private final UserService userService;

    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyDto create(Long userId) {

        User user = userService.findById(userId);

        String prefix = generatePrefix();
        String secret = generateSecret();

        String rawApiKey = buildRawKey(prefix, secret);
        String hash = HashUtils.sha256(secret);

        ApiKey apiKey = new ApiKey();

        apiKey.setUser(user);
        apiKey.setKeyPrefix(prefix);
        apiKey.setKeyHash(hash);

        repository.save(apiKey);

        return new ApiKeyDto(userId, rawApiKey);
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

    public Optional<ApiKey> findByKeyPrefix(String keyPrefix) {
        return repository.findByKeyPrefix(keyPrefix);
    }

    public void validateApiKey(String secret, ApiKey apiKey) {
        String incomingHash = HashUtils.sha256(secret);

        if (!MessageDigest.isEqual(HexFormat.of().parseHex(incomingHash), HexFormat.of().parseHex(apiKey.getKeyHash()))) {
            throw new ResourceNotFoundException();
        }
    }
}
