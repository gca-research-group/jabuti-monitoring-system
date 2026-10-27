package br.edu.unijui.gca.api.execution.adapters.blockchain;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.execution.application.ports.ContractInvoker;
import br.edu.unijui.gca.api.factories.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionMetadata;
import tools.jackson.databind.ObjectMapper;

public class BlockchainContractInvoker implements ContractInvoker {

    private final BlockchainConnectionFactory factory;
    private final ObjectMapper objectMapper;
    private final ExecutionMetadata metadata;

    public BlockchainContractInvoker(BlockchainConnectionFactory factory, ObjectMapper objectMapper,
                                     ExecutionMetadata metadata) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        this.metadata = metadata;
    }

    @Override
    public String invoke(Blockchain blockchain, SmartContractPayloadDto payload) {
        // Resolve configuration and client before contract lookup to preserve failure precedence.
        var parameters = objectMapper.convertValue(blockchain.getParameters(), factory.getConfigType(blockchain.getPlatform()));
        var connection = factory.getInstance(blockchain.getPlatform());
        var contract = metadata.findSmartContract(payload.getSmartContractId());

        return connection.invoke(payload.getBlockchainId(), parameters, contract.getName(),
                payload.getClauseName(), payload.getClauseArguments());
    }
}
