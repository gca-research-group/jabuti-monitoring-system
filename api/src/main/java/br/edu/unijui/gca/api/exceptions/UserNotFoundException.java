package br.edu.unijui.gca.api.exceptions;

public class UserNotFoundException  extends ApplicationException {
    public UserNotFoundException() {
        super("USER_NOT_FOUND");
    }
}
