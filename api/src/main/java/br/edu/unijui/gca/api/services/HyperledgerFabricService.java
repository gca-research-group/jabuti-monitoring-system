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
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@NoArgsConstructor
public class HyperledgerFabricService implements IBlockchainConnection<Gateway.Builder, HyperledgerFabricConfigDto> {

    private final Map<String, Gateway.Builder> connections = new HashMap<>();

    public Gateway.Builder getConnection(String blockchainId, HyperledgerFabricConfigDto parameters) {
        return connections.computeIfAbsent(blockchainId, id -> connect(parameters));
    }

    @Override
    public Gateway.Builder connect(HyperledgerFabricConfigDto config) throws BlockchainConnectionException {
        try {
            return Gateway.newInstance()
                    .identity(loadIdentity(config))
                    .signer(loadSigner(config))
                    .evaluateOptions(opts ->
                            opts.withDeadlineAfter(20, TimeUnit.SECONDS)
                    )
                    .endorseOptions(opts ->
                            opts.withDeadlineAfter(30, TimeUnit.SECONDS)
                    )
                    .submitOptions(opts ->
                            opts.withDeadlineAfter(20, TimeUnit.SECONDS)
                    )
                    .commitStatusOptions(opts ->
                            opts.withDeadlineAfter(120, TimeUnit.SECONDS)
                    );
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

    public String invoke(Gateway.Builder builder,
                         HyperledgerFabricConfigDto config,
                         String smartContractName,
                         String clauseName,
                         List<SmartContractClauseArgumentDto> clauseArguments
                        ) {
        try {
            ManagedChannel grpcChannel = loadChannel(config);

            try(Gateway gateway = builder.connection(grpcChannel).connect()) {
                Network network = gateway.getNetwork(config.getChannelName());
                Contract contract = network.getContract(smartContractName);
                var values = clauseArguments.stream().map(SmartContractClauseArgumentDto::getValue).toList();
                byte[] result = contract.submitTransaction(clauseName, values.toArray(new String[0]));
                return new String(result, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                grpcChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            log.error("[HyperledgerFabricService >> invoke] {}", ex.getMessage());
            throw new SmartContractInvokeException(ex.getMessage());
        }
    }
}
