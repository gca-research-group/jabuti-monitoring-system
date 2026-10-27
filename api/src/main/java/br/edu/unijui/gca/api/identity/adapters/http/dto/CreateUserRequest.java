package br.edu.unijui.gca.api.identity.adapters.http.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest extends IdentityRequest {
    @NotBlank
    private String name;
    @NotBlank
    @Email
    private String email;
    @NotEmpty
    private String password;
    @Size(max = 255)
    private String photo;
    @NotNull
    private Boolean status = true;
    private String remarks;
}
