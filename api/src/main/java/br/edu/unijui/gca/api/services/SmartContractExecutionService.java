package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class SmartContractExecutionService extends BaseService<
        SmartContractExecution,
        UUID,
        SmartContractExecutionFilterDto,
        SmartContractExecutionDto> {

    private final AmqpTemplate amqpTemplate;

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

    public void execute(SmartContractQueueInboundEventDto event) {
        var timestamps = new HashMap<SmartContractExecutionEvent, String>();
        timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());

        SmartContractExecutionDto smartContractExecutionDto = SmartContractExecutionDto.builder()
                .status(SmartContractExecutionStatus.PUBLISHED)
                .metadata(event.getMetadata())
                .timestamps(timestamps)
                .build();

        SmartContractExecution smartContractExecution = create(smartContractExecutionDto);

        event.setId(smartContractExecution.getId());

        amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.INBOUND_ROUTING_KEY,
                event
        );
    }
}