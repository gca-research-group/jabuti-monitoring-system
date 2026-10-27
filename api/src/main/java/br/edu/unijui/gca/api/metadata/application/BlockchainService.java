package br.edu.unijui.gca.api.metadata.application;

import br.edu.unijui.gca.api.shared.application.BaseService;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainConfigDto;
import br.edu.unijui.gca.api.dtos.blockchain.BlockchainDto;
import br.edu.unijui.gca.api.dtos.blockchain.BlockchainFilterDto;
import br.edu.unijui.gca.api.dtos.blockchain.BlockchainPlatformDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.mappers.BlockchainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Profile("metadata-management")
@Service
public class BlockchainService extends BaseService<Blockchain, UUID, BlockchainFilterDto, BlockchainDto> {

    private final BlockchainStore store;

    private final BlockchainMapper mapper;

    @Override
    protected BlockchainStore store() {
        return store;
    }

    @Override
    protected BlockchainMapper mapper() {
        return mapper;
    }

    private HashMap<BlockchainPlatform, List<BlockchainConfigDto>> getConfigs() {
        var configs = new HashMap<BlockchainPlatform, List<BlockchainConfigDto>>();
        var mspId = BlockchainConfigDto.builder().field("mspId").type("string").description("Org1MSP").build();
        var peerEndpoint = BlockchainConfigDto.builder().field("peerEndpoint").type("string").description("localhost:7051").build();
        var peerHostAlias = BlockchainConfigDto.builder().field("peerHostAlias").type("string").description("peer0.org1.example.com").build();
        var channelName = BlockchainConfigDto.builder().field("channelName").type("string").build();
        var signCert = BlockchainConfigDto.builder()
                .field("signCert")
                .type("text")
                .description("You can find it at crypto-materials/peerOrganizations/org*/users/User*@org*/msp/signcerts")
                .build();
        var keyStore = BlockchainConfigDto.builder()
                .field("keyStore")
                .type("text")
                .description("You can find it at crypto-materials/peerOrganizations/org*/users/User*@org*/msp/keystore")
                .build();
        var caCrt = BlockchainConfigDto.builder()
                .field("caCrt")
                .type("text")
                .description("You can find it at crypto-materials/peerOrganizations/org*/peers/peer*/tls/ca.crt")
                .build();

        configs.put(BlockchainPlatform.HYPERLEDGER_FABRIC, List.of(mspId, peerEndpoint, peerHostAlias, channelName, signCert, keyStore, caCrt));

        return configs;
    }

    public List<BlockchainConfigDto> config(BlockchainPlatform platform) {
        HashMap<BlockchainPlatform, List<BlockchainConfigDto>> configs = getConfigs();
        return configs.get(platform);
    }

    public List<BlockchainPlatformDto> platforms(String backendUrl) {
        var dummy = BlockchainPlatformDto.builder()
                .id(BlockchainPlatform.DUMMY)
                .name(BlockchainPlatform.DUMMY)
                .image(backendUrl.concat("/images/blockchain/dummy.png"))
                .build();

        var hyperledgerFabric = BlockchainPlatformDto.builder()
                .id(BlockchainPlatform.HYPERLEDGER_FABRIC)
                .name(BlockchainPlatform.HYPERLEDGER_FABRIC)
                .image(backendUrl.concat("/images/blockchain/hyperledger.png"))
                .build();

        return List.of(dummy, hyperledgerFabric);
    }
}
