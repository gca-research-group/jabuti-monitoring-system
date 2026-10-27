package br.edu.unijui.gca.api.identity.adapters.http.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateApiKeyRequest extends IdentityRequest {
    @NotNull
    private Long userId;
}
