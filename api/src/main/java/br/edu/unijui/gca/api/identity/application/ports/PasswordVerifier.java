package br.edu.unijui.gca.api.identity.application.ports;

public interface PasswordVerifier {
    void validatePassword(String password, String encodedPassword);
}
