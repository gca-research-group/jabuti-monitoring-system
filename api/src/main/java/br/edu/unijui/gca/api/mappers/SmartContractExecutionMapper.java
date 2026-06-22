package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SmartContractExecutionMapper extends IMapper<SmartContractExecution, SmartContractExecutionDto> {
}
