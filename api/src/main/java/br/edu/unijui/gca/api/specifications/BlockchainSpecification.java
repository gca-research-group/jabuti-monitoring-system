package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainFilterDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import org.springframework.stereotype.Component;


@Component
public class BlockchainSpecification extends BaseSpecification<Blockchain, BlockchainFilterDto> {
    @Override
    protected Class<BlockchainFilterDto> getFilterClass() {
        return BlockchainFilterDto.class;
    }
}
