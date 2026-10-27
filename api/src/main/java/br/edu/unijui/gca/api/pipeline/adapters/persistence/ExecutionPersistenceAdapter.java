package br.edu.unijui.gca.api.pipeline.adapters.persistence;

import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionManagementStore;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import br.edu.unijui.gca.api.shared.adapters.persistence.BasePersistenceAdapter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class ExecutionPersistenceAdapter extends BasePersistenceAdapter<SmartContractExecution, UUID, SmartContractExecutionFilterDto>
        implements ExecutionManagementStore {
    private final SmartContractExecutionRepository repository;
    private final SmartContractExecutionSpecification specification;
    private final SmartContractExecutionMapper mapper;

    @Override
    protected SmartContractExecutionRepository repository() {
        return repository;
    }

    @Override
    protected SmartContractExecutionSpecification specification() {
        return specification;
    }

    @Override
    public SmartContractExecution create(SmartContractExecutionDto execution) {
        return save(mapper.toEntity(execution));
    }

    @Override
    public void update(SmartContractExecution execution) {
        save(execution);
    }

    @Override
    public void removeAll() {
        repository.deleteAllInBatch();
    }
}
