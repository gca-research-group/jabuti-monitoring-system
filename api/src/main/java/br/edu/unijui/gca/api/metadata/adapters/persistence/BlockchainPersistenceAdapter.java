package br.edu.unijui.gca.api.metadata.adapters.persistence;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainFilterDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.repositories.BlockchainRepository;
import br.edu.unijui.gca.api.specifications.BlockchainSpecification;
import br.edu.unijui.gca.api.shared.adapters.persistence.BasePersistenceAdapter;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@RequiredArgsConstructor
public class BlockchainPersistenceAdapter extends BasePersistenceAdapter<Blockchain, UUID, BlockchainFilterDto> implements BlockchainStore {
    private final BlockchainRepository repository;
    private final BlockchainSpecification specification;

    @Override
    protected BlockchainRepository repository() {
        return repository;
    }

    @Override
    protected BlockchainSpecification specification() {
        return specification;
    }
}
