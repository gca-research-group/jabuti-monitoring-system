package br.edu.unijui.gca.api.exceptions;

public class ApiKeyNotFoundException extends ApplicationException {
    public ApiKeyNotFoundException() {
        super("API_KEY_NOT_FOUND");
    }
}
