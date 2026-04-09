package br.edu.unijui.gca.api.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponseDto {
    private Long id;
    private String name;
    private String email;
    private String accessToken;
}
