package br.edu.unijui.gca.api.metadata.application.ports;

import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.shared.application.ports.ResourceStore;
import java.util.UUID;

public interface ExecutionManagementStore extends ExecutionStore, ResourceStore<SmartContractExecution, UUID, SmartContractExecutionFilterDto> {
    void removeAll();
}
