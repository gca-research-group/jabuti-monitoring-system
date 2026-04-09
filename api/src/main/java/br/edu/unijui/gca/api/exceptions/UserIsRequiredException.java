package br.edu.unijui.gca.api.exceptions;

public class UserIsRequiredException  extends ApplicationException {
    public UserIsRequiredException() {
        super("USER_IS_REQUIRED");
    }
}
