package br.edu.unijui.gca.api.pipeline.application.ports;

import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;

import java.util.UUID;

public interface ExecutionStore {
    SmartContractExecution create(SmartContractExecutionDto execution);
    SmartContractExecution findById(UUID id);
    void update(SmartContractExecution execution);
}
