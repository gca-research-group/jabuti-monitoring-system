package br.edu.unijui.gca.api.exceptions;

public class TokenNotFoundException extends ApplicationException {
    public TokenNotFoundException() {
        super("TOKEN_NOT_FOUND");
    }
}
