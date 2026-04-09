package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.BlockchainDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.interfaces.IMapper;
import org.springframework.stereotype.Component;

@Component
public class BlockchainMapper implements IMapper<Blockchain, BlockchainDto> {

    @Override
    public Blockchain toEntity(Blockchain blockchain, BlockchainDto dto) {
        blockchain.setId(dto.getId());
        blockchain.setRemarks(dto.getRemarks());
        blockchain.setPlatform(dto.getPlatform());
        blockchain.setParameters(dto.getParameters());
        blockchain.setName(dto.getName());

        return blockchain;
    }

    @Override
    public Blockchain toEntity(BlockchainDto dto) {
        return toEntity(new Blockchain(), dto);
    }

    @Override
    public BlockchainDto toDto(Blockchain blockchain) {
        return BlockchainDto.builder()
                .id(blockchain.getId())
                .name(blockchain.getName())
                .platform(blockchain.getPlatform())
                .parameters(blockchain.getParameters())
                .build();
    }


}
