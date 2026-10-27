package br.edu.unijui.gca.api.identity.application.ports;

import br.edu.unijui.gca.api.entities.User;

public interface TokenOperations {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
    String getSubject(String token);
    boolean isTokenInvalid(String token, User user);
}
