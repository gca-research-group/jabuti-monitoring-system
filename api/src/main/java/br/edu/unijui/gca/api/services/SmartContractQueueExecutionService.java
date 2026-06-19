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

import java.time.Instant;
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
        Instant consumedAt = Instant.now();
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(payload.getId());

        try {
            smartContractExecution.setExecutionQueueConsumedAt(consumedAt);
            smartContractExecution.setExecutionQueueProcessingStartedAt(Instant.now());
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
            smartContractExecution.setExecutionQueueProcessedAt(Instant.now());

            smartContractExecutionService.update(smartContractExecution);

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

            smartContractExecution.setOutboundQueuePublishedAt(Instant.now());
            smartContractExecutionService.update(smartContractExecution);

        } catch(Throwable t) {
            smartContractExecution.setStatus("ERROR");
            smartContractExecution.setResult(objectMapper.writeValueAsString(Map.of("error", t.getMessage())));

            amqpTemplate.convertAndSend(
                QueueNames.MAIN_EXCHANGE,
                QueueNames.OUTBOUND_ROUTING_KEY,
                smartContractExecutionMapper.toDto(smartContractExecution));

            throw new AmqpRejectAndDontRequeueException(t);
        }
    }
}
