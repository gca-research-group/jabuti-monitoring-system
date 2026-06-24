package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.config.QueueNames;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.factories.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Slf4j
@Component
public class SmartContractQueueExecutionService {
    @Autowired
    private BlockchainConnectionFactory blockchainConnectionFactory;

    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @Autowired
    private SmartContractExecutionMapper smartContractExecutionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @RabbitListener(queues = {QueueNames.EXECUTION_QUEUE})
    public void process(SmartContractPayloadDto payload) {
        OffsetDateTime consumedAt = OffsetDateTime.now(ZoneOffset.UTC);
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(payload.getId());

        Map<String, String> timestamps = smartContractExecution.getTimestamps();

        try {
            timestamps.put("execution_queue.consumed", consumedAt.toString());
            timestamps.put("execution_queue.processing", OffsetDateTime.now(ZoneOffset.UTC).toString());

            smartContractExecution.setTimestamps(timestamps);
            smartContractExecution.setStatus("PROCESSING");
            smartContractExecutionService.update(smartContractExecution);

            var parameters = objectMapper.convertValue(payload.getBlockchain().getParameters(),
                    blockchainConnectionFactory.getConfigType(payload.getBlockchain().getPlatform()));

            var service = blockchainConnectionFactory.getInstance(payload.getBlockchain().getPlatform());

            var connection = service.getConnection(payload.getBlockchain().getId().toString(), parameters);

            var result = service.invoke(connection,
                    parameters,
                    payload.getSmartContract().getName(),
                    payload.getClauseName(),
                    payload.getClauseArguments());

            smartContractExecution.setStatus("SUCCESS");
            smartContractExecution.setResult(result);

            timestamps.put("execution_queue.processed", OffsetDateTime.now(ZoneOffset.UTC).toString());
            timestamps.put("outbound_queue.published", OffsetDateTime.now(ZoneOffset.UTC).toString());

            smartContractExecution.setTimestamps(timestamps);

            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));
        } catch(Throwable t) {
            smartContractExecution.setStatus("ERROR");
            smartContractExecution.setResult(objectMapper.writeValueAsString(Map.of("error", t.getMessage())));
            timestamps.put("outbound_queue.published", OffsetDateTime.now(ZoneOffset.UTC).toString());
            smartContractExecution.setTimestamps(timestamps);
            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

            throw new AmqpRejectAndDontRequeueException(t);
        }
    }
}
