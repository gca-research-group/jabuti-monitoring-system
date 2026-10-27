package br.edu.unijui.gca.api.identity.adapters.http.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest extends IdentityRequest {
    @NotBlank
    @Email
    private String email;
    @NotEmpty
    private String password;
}
