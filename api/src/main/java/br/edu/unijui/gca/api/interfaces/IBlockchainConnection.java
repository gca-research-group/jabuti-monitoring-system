package br.edu.unijui.gca.api.interfaces;

import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.exceptions.BlockchainConnectionException;
import br.edu.unijui.gca.api.exceptions.SmartContractInvokeException;

import java.util.List;
import java.util.UUID;

public interface IBlockchainConnection<ConnectionType, ConfigType> {
    ConnectionType connect(ConfigType config) throws BlockchainConnectionException;
    ConnectionType getConnection(UUID blockchainId, ConfigType config);
    String invoke(UUID blockchainId,
                  ConfigType config,
                  String smartContractName,
                  String clauseName,
                  List<SmartContractClauseArgumentDto> clauseArguments) throws SmartContractInvokeException;
}
