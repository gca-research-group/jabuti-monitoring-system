package br.edu.unijui.gca.api.exceptions;

public class ExpiredTokenException  extends ApplicationException {
    public ExpiredTokenException() {
        super("TOKEN_EXPIRED");
    }
}
