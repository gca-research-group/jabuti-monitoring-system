package br.edu.unijui.gca.api.valueobjects;

import br.edu.unijui.gca.api.exceptions.InvalidTokenException;

public record BearerToken(String token) implements AuthToken {

    public BearerToken {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException();
        }
    }

    public static BearerToken from(String authorizationHeader) {
        if (!isBearerToken(authorizationHeader)) {
            throw new InvalidTokenException();
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        return new BearerToken(token);
    }

    public static boolean isBearerToken(String token) {
        return token != null && token.startsWith("Bearer ");
    }
}
