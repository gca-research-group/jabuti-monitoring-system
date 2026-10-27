package br.edu.unijui.gca.api.metadata.application;

import br.edu.unijui.gca.api.shared.application.BaseService;
import br.edu.unijui.gca.api.pipeline.application.ports.ExecutionManagementStore;

import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Slf4j
@RequiredArgsConstructor
@Profile("metadata-management")
@Service
public class SmartContractExecutionService extends BaseService<
        SmartContractExecution,
        UUID,
        SmartContractExecutionFilterDto,
        SmartContractExecutionDto> {

    private final ExecutionManagementStore store;

    private final SmartContractExecutionMapper mapper;

    @Override
    protected ExecutionManagementStore store() {
        return store;
    }

    @Override
    protected SmartContractExecutionMapper mapper() {
        return mapper;
    }

    public void removeAll() {
        store.removeAll();
    }

}
