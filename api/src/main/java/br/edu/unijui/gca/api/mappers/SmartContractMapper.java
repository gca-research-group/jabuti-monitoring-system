package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.SmartContractDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SmartContractMapper extends IMapper<SmartContract, SmartContractDto> {
}
