package br.edu.unijui.gca.api.metadata.application.ports;

import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import java.util.UUID;

public interface SmartContractStore extends ResourceStore<SmartContract, UUID, SmartContractFilterDto> {
}
