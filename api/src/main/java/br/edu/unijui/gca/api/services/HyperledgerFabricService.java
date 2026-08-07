package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.HyperledgerFabricConfigDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.exceptions.BlockchainConnectionException;
import br.edu.unijui.gca.api.exceptions.SmartContractInvokeException;
import br.edu.unijui.gca.api.interfaces.IBlockchainConnection;
import io.grpc.ChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HyperledgerFabricService implements IBlockchainConnection<Gateway, HyperledgerFabricConfigDto> {

    private final Map<UUID, Gateway> activeGateways = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> activeChannels = new ConcurrentHashMap<>();

    public Gateway getConnection(UUID blockchainId, HyperledgerFabricConfigDto parameters) {
        return activeGateways.computeIfAbsent(blockchainId, id -> connect(parameters));
    }

    @Override
    public Gateway connect(HyperledgerFabricConfigDto config) throws BlockchainConnectionException {
        try {

            ManagedChannel grpcChannel = activeChannels.computeIfAbsent(config.getPeerEndpoint(), endpoint -> {
                try {
                    return loadChannel(config);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to initialize gRPC Channel", e);
                }
            });

            return Gateway.newInstance()
                    .identity(loadIdentity(config))
                    .signer(loadSigner(config))
                    .connection(grpcChannel) // Attach long-lived channel
                    .evaluateOptions(opts -> opts.withDeadlineAfter(20, TimeUnit.SECONDS))
                    .endorseOptions(opts -> opts.withDeadlineAfter(30, TimeUnit.SECONDS))
                    .submitOptions(opts -> opts.withDeadlineAfter(20, TimeUnit.SECONDS))
                    .commitStatusOptions(opts -> opts.withDeadlineAfter(120, TimeUnit.SECONDS))
                    .connect();

        } catch (Exception e) {
            log.error("[HyperledgerFabricService >> connect] {}", e.getMessage());
            throw new BlockchainConnectionException();
        }
    }

    private Identity loadIdentity(HyperledgerFabricConfigDto config) throws Exception {
        Reader certReader = new StringReader(config.getSignCert());
        X509Certificate certificate = Identities.readX509Certificate(certReader);
        return new X509Identity(config.getMspId(), certificate);
    }

    private Signer loadSigner(HyperledgerFabricConfigDto config) throws Exception {
        Reader keyReader = new StringReader(config.getKeyStore());
        PrivateKey privateKey = Identities.readPrivateKey(keyReader);
        return Signers.newPrivateKeySigner(privateKey);
    }

    private ManagedChannel loadChannel(HyperledgerFabricConfigDto config) throws IOException {
        ChannelCredentials tlsCredentials = TlsChannelCredentials.newBuilder()
                .trustManager(new ByteArrayInputStream(config.getCaCrt().getBytes(StandardCharsets.UTF_8)))
                .build();

        return Grpc.newChannelBuilder(config.getPeerEndpoint(), tlsCredentials)
                .overrideAuthority(config.getPeerHostAlias())
                .keepAliveTime(30000, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(10000, TimeUnit.MILLISECONDS)
                .build();
    }

    public String invoke(UUID blockchainId,
                         HyperledgerFabricConfigDto config,
                         String smartContractName,
                         String clauseName,
                         List<SmartContractClauseArgumentDto> clauseArguments
                        ) {
        try {
            try {
                Gateway gateway = getConnection(blockchainId, config);

                Network network = gateway.getNetwork(config.getChannelName());
                Contract contract = network.getContract(smartContractName);

                var values = clauseArguments.stream()
                        .map(SmartContractClauseArgumentDto::getValue)
                        .toList();

                byte[] result = contract.submitTransaction(clauseName, values.toArray(new String[0]));
                return new String(result, StandardCharsets.UTF_8);

            } catch (Exception ex) {
                log.error("[HyperledgerFabricService >> invoke] {}", ex.getMessage());
                throw new SmartContractInvokeException(ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("[HyperledgerFabricService >> invoke] {}", ex.getMessage());
            throw new SmartContractInvokeException(ex.getMessage());
        }
    }
}
