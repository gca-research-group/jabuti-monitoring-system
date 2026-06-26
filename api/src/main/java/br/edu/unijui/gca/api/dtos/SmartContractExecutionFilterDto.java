package br.edu.unijui.gca.api.dtos;

import br.edu.unijui.gca.api.dtos.filter.BaseFilterDto;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SmartContractExecutionFilterDto extends BaseFilterDto {
    private UUID id;
    private String status;
    private String blockchainPlatform;
    private String smartContractName;
}
