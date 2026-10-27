package br.edu.unijui.gca.api.pipeline.execution.adapters.blockchain;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.pipeline.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.pipeline.execution.adapters.blockchain.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import tools.jackson.databind.ObjectMapper;

public class BlockchainContractInvoker implements ContractInvoker {

    private final BlockchainConnectionFactory factory;
    private final ObjectMapper objectMapper;
    private final SmartContractStore contracts;

    public BlockchainContractInvoker(BlockchainConnectionFactory factory, ObjectMapper objectMapper,
                                     SmartContractStore contracts) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        this.contracts = contracts;
    }

    @Override
    public String invoke(Blockchain blockchain, SmartContractPayloadDto payload) {
        // Resolve configuration and client before contract lookup to preserve failure precedence.
        var parameters = objectMapper.convertValue(blockchain.getParameters(), factory.getConfigType(blockchain.getPlatform()));
        var connection = factory.getInstance(blockchain.getPlatform());
        var contract = contracts.findById(payload.getSmartContractId());

        return connection.invoke(payload.getBlockchainId(), parameters, contract.getName(),
                payload.getClauseName(), payload.getClauseArguments());
    }
}
