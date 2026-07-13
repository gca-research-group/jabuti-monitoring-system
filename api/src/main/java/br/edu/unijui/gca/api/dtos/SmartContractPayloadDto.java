package br.edu.unijui.gca.api.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SmartContractPayloadDto {
    private UUID id;
    private UUID blockchainId;
    private UUID smartContractId;
    private String clauseName;
    private List<SmartContractClauseArgumentDto> clauseArguments;
}