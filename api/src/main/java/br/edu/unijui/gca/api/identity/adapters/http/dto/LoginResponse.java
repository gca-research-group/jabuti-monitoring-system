package br.edu.unijui.gca.api.identity.adapters.http.dto;


import br.edu.unijui.gca.api.entities.User;

public record LoginResponse (
    Long id,
    String name,
    String email,
    String accessToken) {

    static public LoginResponse from(User user, String accessToken) {
        return new LoginResponse(user.getId(), user.getName(), user.getEmail(), accessToken);
    }

}
