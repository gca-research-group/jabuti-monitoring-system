package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;

@Service
public class SmartContractExecutionService extends BaseService<
        SmartContractExecution,
        UUID,
        SmartContractExecutionDto,
        SmartContractExecutionFilterDto,
        SmartContractExecutionRepository,
        SmartContractExecutionSpecification,
        SmartContractExecutionMapper> {

    @Autowired
    private AmqpTemplate amqpTemplate;

    public void removeAll() {
        repository.deleteAllInBatch();
    }

    public void execute(SmartContractQueueInboundEventDto event) {
        var timestamps = new HashMap<String, String>();
        timestamps.put("inbound_queue.published", OffsetDateTime.now(ZoneOffset.UTC).toString());

        SmartContractExecutionDto smartContractExecutionDto = SmartContractExecutionDto.builder()
                .status("PENDING")
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