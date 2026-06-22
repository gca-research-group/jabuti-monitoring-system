package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionEventDto;
import br.edu.unijui.gca.api.entities.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SmartContractExecutionEventMapper extends IMapper<SmartContractExecutionEvent, SmartContractExecutionEventDto>  {
}
