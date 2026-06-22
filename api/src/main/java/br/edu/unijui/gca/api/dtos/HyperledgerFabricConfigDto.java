package br.edu.unijui.gca.api.dtos;


import br.edu.unijui.gca.api.interfaces.IBlockchainConnectionParameters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyperledgerFabricConfigDto implements IBlockchainConnectionParameters  {
    private String mspId;
    private String peerEndpoint;
    private String peerHostAlias;
    private String caCrt;
    private String keyStore;
    private String signCert;
    private String channelName;
}
