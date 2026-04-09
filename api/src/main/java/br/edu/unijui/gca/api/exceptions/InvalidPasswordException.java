package br.edu.unijui.gca.api.exceptions;

public class InvalidPasswordException extends ApplicationException {
    public InvalidPasswordException() {
        super("INVALID_PASSWORD");
    }
}
