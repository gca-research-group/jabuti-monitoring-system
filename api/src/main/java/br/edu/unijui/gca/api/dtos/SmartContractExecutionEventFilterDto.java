package br.edu.unijui.gca.api.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SmartContractExecutionEventFilterDto {
    private String name;
    private UUID smartContractExecutionId;
}
