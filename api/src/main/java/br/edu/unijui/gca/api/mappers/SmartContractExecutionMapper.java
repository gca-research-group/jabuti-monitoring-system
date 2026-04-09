package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.springframework.stereotype.Component;

@Component
public class SmartContractExecutionMapper implements IMapper<SmartContractExecution, SmartContractExecutionDto> {
    @Override
    public SmartContractExecution toEntity(SmartContractExecution smartContractExecution, SmartContractExecutionDto dto) {
        smartContractExecution.setMetadata(dto.getMetadata());
        smartContractExecution.setRemarks(dto.getRemarks());
        smartContractExecution.setResult(dto.getResult());
        smartContractExecution.setStatus(dto.getStatus());
        smartContractExecution.setPayload(dto.getPayload());

        return smartContractExecution;
    }

    @Override
    public SmartContractExecution toEntity(SmartContractExecutionDto dto) {
        return toEntity(new SmartContractExecution(), dto);
    }

    @Override
    public SmartContractExecutionDto toDto(SmartContractExecution smartContractExecution) {
        return SmartContractExecutionDto.builder()
                .id(smartContractExecution.getId())
                .payload(smartContractExecution.getPayload())
                .metadata(smartContractExecution.getMetadata())
                .result(smartContractExecution.getResult())
                .status(smartContractExecution.getStatus())
                .build();
    }
}
