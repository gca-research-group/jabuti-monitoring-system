package br.edu.unijui.gca.api.dtos;

import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PostExecutionActionDto {

    @NotNull
    private PostExecutionActionType type;

    private String url;

    private UUID blockchainId;

    private UUID smartContractId;

    private String clauseName;
}
