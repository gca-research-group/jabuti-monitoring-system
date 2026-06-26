package br.edu.unijui.gca.api.dtos.filter;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class SmartContractFilterDto extends BaseFilterDto {
    private String name;
}
