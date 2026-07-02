package br.edu.unijui.gca.api.dtos;


import br.edu.unijui.gca.api.entities.User;

public record AuthResponseDto (
    Long id,
    String name,
    String email,
    String accessToken) {

    static public AuthResponseDto from(User user, String accessToken) {
        return new AuthResponseDto(user.getId(), user.getName(), user.getEmail(), accessToken);
    }

}
