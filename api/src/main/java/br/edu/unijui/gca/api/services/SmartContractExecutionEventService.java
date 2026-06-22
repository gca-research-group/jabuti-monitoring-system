package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionEventDto;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionEventFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.entities.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionEventMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionEventRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionEventSpecification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class SmartContractExecutionEventService extends BaseService<
        SmartContractExecutionEvent,
        UUID,
        SmartContractExecutionEventDto,
        SmartContractExecutionEventFilterDto,
        SmartContractExecutionEventRepository,
        SmartContractExecutionEventSpecification,
        SmartContractExecutionEventMapper> {

    public SmartContractExecutionEvent create(SmartContractExecution smartContractExecution, String name, Instant eventAt) {
        SmartContractExecutionEventDto dto = SmartContractExecutionEventDto.builder()
                .smartContractExecutionId(smartContractExecution.getId())
                .name(name)
                .eventAt(eventAt)
                .build();

        return create(dto);
    }
}
