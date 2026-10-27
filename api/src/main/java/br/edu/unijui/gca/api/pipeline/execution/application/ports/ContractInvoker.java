package br.edu.unijui.gca.api.pipeline.execution.application.ports;

import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.Blockchain;

public interface ContractInvoker {
    String invoke(Blockchain blockchain, SmartContractPayloadDto payload);
}
