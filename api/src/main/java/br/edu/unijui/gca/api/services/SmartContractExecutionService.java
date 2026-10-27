package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Slf4j
@RequiredArgsConstructor
@Service
public class SmartContractExecutionService extends BaseService<
        SmartContractExecution,
        UUID,
        SmartContractExecutionFilterDto,
        SmartContractExecutionDto> {

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
    protected SmartContractExecutionMapper mapper() {
        return mapper;
    }

    public void removeAll() {
        repository.deleteAllInBatch();
    }

}
