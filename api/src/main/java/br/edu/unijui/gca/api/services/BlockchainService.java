package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.BlockchainConfigDto;
import br.edu.unijui.gca.api.dtos.BlockchainDto;
import br.edu.unijui.gca.api.dtos.BlockchainFilterDto;
import br.edu.unijui.gca.api.dtos.BlockchainPlatformDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.mappers.BlockchainMapper;
import br.edu.unijui.gca.api.repositories.BlockchainRepository;
import br.edu.unijui.gca.api.specifications.BlockchainSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Service
public class BlockchainService
        extends BaseService<
            Blockchain, UUID, BlockchainDto, BlockchainFilterDto, BlockchainRepository, BlockchainSpecification, BlockchainMapper> {

    @Value("${app.backend-url}")
    private String backendUrl;

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

    public List<BlockchainPlatformDto> platforms() {
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
