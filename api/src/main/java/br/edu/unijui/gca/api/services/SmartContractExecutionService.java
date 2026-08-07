package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import br.edu.unijui.gca.api.repositories.SmartContractExecutionRepository;
import br.edu.unijui.gca.api.specifications.SmartContractExecutionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
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

    private final Queue<SmartContractQueueInboundEventDto> queue = new ConcurrentLinkedQueue<>();

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

    public UUID execute(SmartContractQueueInboundEventDto event) {
        var id = UUID.ofEpochMillis(System.currentTimeMillis());
        event.setId(id);
        queue.offer(event);
        return id;
    }

    @Scheduled(fixedRate = 5)
    public void flushToRabbitMQ() {
        if (queue.isEmpty()) {
            return;
        }

        int count = 0;
        SmartContractQueueInboundEventDto event;

        while (count < 500 && (event = queue.poll()) != null) {
            var timestamps = new HashMap<SmartContractExecutionEvent, String>();
            timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
            event.setTimestamps(timestamps);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.INBOUND_ROUTING_KEY,
                event
            );
            count++;
        }

        if (count > 0) {
            log.debug("Flushed {} messages to RabbitMQ. Remaining in queue: {}", count, queue.size());
        }
    }
}
