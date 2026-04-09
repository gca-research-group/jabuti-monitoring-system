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
    private BlockchainDto blockchain;
    private SmartContractDto smartContract;
    private String clauseName;
    private List<SmartContractClauseArgumentDto> clauseArguments;
}