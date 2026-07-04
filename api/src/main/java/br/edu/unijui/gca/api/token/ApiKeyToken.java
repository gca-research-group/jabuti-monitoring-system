package br.edu.unijui.gca.api.token;

import br.edu.unijui.gca.api.exceptions.InvalidTokenException;

import java.util.Objects;

public record ApiKeyToken(String prefix, String secret) {
    public ApiKeyToken {
        if (Objects.isNull(prefix) || Objects.isNull(secret)) {
            throw new InvalidTokenException();
        }
    }

    public static ApiKeyToken from(String token) {
        if (token == null || !token.startsWith("jms_") || !token.contains(".")) {
            throw new IllegalArgumentException("Invalid API key format");
        }

        String withoutPrefix = token.substring(4);
        String[] parts = withoutPrefix.split("\\.");

        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid API key format");
        }

        return new ApiKeyToken(parts[0], parts[1]);
    }
}
