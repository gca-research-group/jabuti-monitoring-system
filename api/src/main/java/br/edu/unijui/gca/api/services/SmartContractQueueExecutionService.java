package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.factories.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.mappers.SmartContractExecutionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @RabbitListener(queues = {"smart-contract-execution-queue"})
    public void process(SmartContractPayloadDto payload) throws JsonProcessingException {
        SmartContractExecution smartContractExecution = smartContractExecutionService.findById(payload.getId());

        try {
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

            amqpTemplate.convertAndSend(
                    "smart-contract-exchange",
                    "smart-contract-outbound-routing-key",
                    smartContractExecutionMapper.toDto(smartContractExecution));
        } catch(Throwable t) {
            smartContractExecution.setStatus("ERROR");
            smartContractExecution.setResult(objectMapper.writeValueAsString(Map.of("error", t.getMessage())));

            amqpTemplate.convertAndSend(
                "smart-contract-exchange",
                "smart-contract-outbound-routing-key",
                smartContractExecutionMapper.toDto(smartContractExecution));

            throw new AmqpRejectAndDontRequeueException(t);
        }
    }
}
