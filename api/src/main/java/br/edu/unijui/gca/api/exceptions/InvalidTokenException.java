package br.edu.unijui.gca.api.exceptions;

public class InvalidTokenException extends ApplicationException {
    public InvalidTokenException() {
        super("INVALID_TOKEN");
    }
}
