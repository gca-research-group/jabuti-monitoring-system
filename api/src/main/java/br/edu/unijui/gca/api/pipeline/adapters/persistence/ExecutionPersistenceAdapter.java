package br.edu.unijui.gca.api.pipeline.adapters.persistence;

import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionStore;
import br.edu.unijui.gca.api.services.SmartContractExecutionService;

import java.util.UUID;

public class ExecutionPersistenceAdapter implements ExecutionStore {

    private final SmartContractExecutionService service;

    public ExecutionPersistenceAdapter(SmartContractExecutionService service) {
        this.service = service;
    }

    @Override
    public SmartContractExecution create(SmartContractExecutionDto execution) {
        return service.create(execution);
    }

    @Override
    public SmartContractExecution findById(UUID id) {
        return service.findById(id);
    }

    @Override
    public void update(SmartContractExecution execution) {
        service.update(execution);
    }
}
