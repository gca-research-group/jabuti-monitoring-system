package br.edu.unijui.gca.api.dtos;

import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SmartContractDto extends BaseDto<UUID> {
    @NotBlank
    private String name;

    @NotNull
    private BlockchainPlatform blockchainPlatform;

    private List<SmartContractClauseDto> clauses;

    @NotNull
    private Boolean status;

    private String remarks;
}
