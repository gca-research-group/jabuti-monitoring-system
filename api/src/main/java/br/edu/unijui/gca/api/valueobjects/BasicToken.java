package br.edu.unijui.gca.api.valueobjects;

import br.edu.unijui.gca.api.exceptions.InvalidTokenException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record BasicToken(String email, String password) implements AuthToken {

    public BasicToken {
        if (email == null || password == null) {
            throw new InvalidTokenException();
        }
    }

    public static BasicToken from(String token) {
        if (!isBasicToken(token)) {
            throw new InvalidTokenException();
        }

        String encoded = token.substring("Basic ".length());

        byte[] decodedBytes = Base64.getDecoder().decode(encoded);
        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

        String[] parts = decodedString.split(":", 2);

        if (parts.length != 2) {
            throw new InvalidTokenException();
        }

        return new BasicToken(parts[0], parts[1]);
    }

    public static boolean isBasicToken(String token) {
        return token != null && token.startsWith("Basic ");
    }
}
