package br.edu.unijui.gca.api.identity.application;

import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.exceptions.InvalidTokenException;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;
import br.edu.unijui.gca.api.identity.application.ports.UserStore;
import br.edu.unijui.gca.api.token.ApiKeyToken;
import br.edu.unijui.gca.api.token.BearerToken;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Profile({"intake", "metadata-management"})
@Service
public class AuthenticateRequest {
    private final TokenOperations tokens;
    private final UserStore users;
    private final ApiKeyService apiKeys;

    public User authenticateBearer(BearerToken token) {
        String email = tokens.getSubject(token.token());
        User user = users.findByEmail(email).orElseThrow(ResourceNotFoundException::new);
        if (tokens.isTokenInvalid(token.token(), user)) {
            throw new InvalidTokenException();
        }
        return user;
    }

    public User authenticateApiKey(ApiKeyToken token) {
        ApiKey apiKey = apiKeys.findByKeyPrefix(token.prefix());
        apiKeys.validateApiKey(token.secret(), apiKey.getKeyHash());
        return new User();
    }
}
