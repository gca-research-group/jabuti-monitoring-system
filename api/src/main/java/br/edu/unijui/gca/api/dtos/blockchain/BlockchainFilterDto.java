package br.edu.unijui.gca.api.dtos.blockchain;

import br.edu.unijui.gca.api.dtos.BaseFilterDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class BlockchainFilterDto extends BaseFilterDto {
    private String name;
}
