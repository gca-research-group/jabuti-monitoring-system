package br.edu.unijui.gca.api.valueobjects;

import br.edu.unijui.gca.api.exceptions.InvalidTokenException;

public sealed interface AuthToken permits BasicToken, BearerToken {

    static AuthToken from(String authorization) {
        if (BearerToken.isBearerToken(authorization)) {
            return BearerToken.from(authorization);
        }

        if (BasicToken.isBasicToken(authorization)) {
            return BasicToken.from(authorization);
        }

        throw new InvalidTokenException();
    }
}