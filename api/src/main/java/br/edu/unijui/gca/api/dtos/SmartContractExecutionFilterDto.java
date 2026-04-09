package br.edu.unijui.gca.api.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SmartContractExecutionFilterDto {
    private UUID id;
    private String status;
    private String blockchainPlatform;
    private String smartContractName;
}
