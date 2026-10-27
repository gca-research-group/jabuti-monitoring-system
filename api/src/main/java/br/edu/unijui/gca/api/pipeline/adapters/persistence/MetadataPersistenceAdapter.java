package br.edu.unijui.gca.api.pipeline.adapters.persistence;

import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionMetadata;
import br.edu.unijui.gca.api.services.BlockchainService;
import br.edu.unijui.gca.api.services.SmartContractService;

import java.util.UUID;

public class MetadataPersistenceAdapter implements ExecutionMetadata {

    private final BlockchainService blockchains;
    private final SmartContractService contracts;

    public MetadataPersistenceAdapter(BlockchainService blockchains, SmartContractService contracts) {
        this.blockchains = blockchains;
        this.contracts = contracts;
    }

    @Override
    public Blockchain findBlockchain(UUID id) {
        return blockchains.findById(id);
    }

    @Override
    public SmartContract findSmartContract(UUID id) {
        return contracts.findById(id);
    }
}
