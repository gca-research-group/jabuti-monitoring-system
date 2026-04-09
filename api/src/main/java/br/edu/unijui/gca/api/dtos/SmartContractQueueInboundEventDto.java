package br.edu.unijui.gca.api.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SmartContractQueueInboundEventDto {
    private UUID blockchainId;
    private UUID smartContractId;
    private String clauseName;
    private List<SmartContractClauseArgumentDto> clauseArguments;
}
