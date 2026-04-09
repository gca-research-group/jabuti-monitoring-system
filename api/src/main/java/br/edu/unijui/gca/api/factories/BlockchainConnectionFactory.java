package br.edu.unijui.gca.api.factories;

import br.edu.unijui.gca.api.dtos.HyperledgerFabricConfigDto;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.exceptions.UnsupportedBlockchainPlatformException;
import br.edu.unijui.gca.api.interfaces.IBlockchainConnection;
import br.edu.unijui.gca.api.services.HyperledgerFabricService;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BlockchainConnectionFactory {
    private final Map<BlockchainPlatform, IBlockchainConnection<?, ?>> instances = new EnumMap<>(BlockchainPlatform.class);

    private final Map<String, Object> connections = new ConcurrentHashMap<>();

    public Class<?> getConfigType(BlockchainPlatform platform) {
        if (Objects.requireNonNull(platform) == BlockchainPlatform.HYPERLEDGER_FABRIC) {
            return HyperledgerFabricConfigDto.class;
        }

        throw new UnsupportedBlockchainPlatformException();
    }


    @SuppressWarnings("unchecked")
    public <R, S> IBlockchainConnection<R, S> getInstance(BlockchainPlatform platform) {
        if (!instances.containsKey(platform)) {
            if (Objects.requireNonNull(platform) == BlockchainPlatform.HYPERLEDGER_FABRIC) {
                instances.put(BlockchainPlatform.HYPERLEDGER_FABRIC, new HyperledgerFabricService());
            } else {
                throw new UnsupportedBlockchainPlatformException();
            }
        }

        return (IBlockchainConnection<R, S>) instances.get(platform);
    }

    public Object getConnection(String blockchainId, BlockchainPlatform blockchainPlatform, Object parameters) {
        var service = getInstance(blockchainPlatform);
        return connections.computeIfAbsent(blockchainId, id -> service.connect(parameters));
    }
}
