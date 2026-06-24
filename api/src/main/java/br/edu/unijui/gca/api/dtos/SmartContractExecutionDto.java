package br.edu.unijui.gca.api.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SmartContractExecutionDto extends BaseDto<UUID> {
    private SmartContractPayloadDto payload;
    private Map<String, Object> metadata;
    private Map<String, String> timestamps;
    private String result;
    private String status;
}
