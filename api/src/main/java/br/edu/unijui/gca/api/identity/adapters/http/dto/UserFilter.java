package br.edu.unijui.gca.api.identity.adapters.http.dto;

import br.edu.unijui.gca.api.dtos.BaseFilterDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserFilter extends BaseFilterDto {
    private String name;
    private String email;
}
