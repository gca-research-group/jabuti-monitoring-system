package br.edu.unijui.gca.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SmartContractExecutionEventDto  extends BaseDto<UUID> {
    private String name;
    private UUID smartContractExecutionId;
    private Instant eventAt;
}
