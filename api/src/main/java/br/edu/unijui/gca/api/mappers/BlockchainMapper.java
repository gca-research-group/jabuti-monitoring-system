package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BlockchainMapper extends IMapper<Blockchain, BlockchainDto> {
}
