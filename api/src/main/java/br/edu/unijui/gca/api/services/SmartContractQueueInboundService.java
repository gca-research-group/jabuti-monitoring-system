package br.edu.unijui.gca.api.services;


import br.edu.unijui.gca.api.dtos.*;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.exceptions.*;
import br.edu.unijui.gca.api.mappers.BlockchainMapper;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SmartContractQueueInboundService {
    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private BlockchainMapper blockchainMapper;

    @Autowired
    private SmartContractService smartContractService;

    @Autowired
    private SmartContractMapper smartContractMapper;

    @Autowired
    private SmartContractExecutionService smartContractExecutionService;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @RabbitListener(queues = {"smart-contract-inbound-queue"})
    public void process(SmartContractQueueInboundEventDto event) {

        SmartContractExecutionDto smartContractExecutionDto =  SmartContractExecutionDto.builder()
            .status("PENDING")
            .metadata(Map.of("event", event))
            .build();

        SmartContractExecution smartContractExecution = smartContractExecutionService.create(smartContractExecutionDto);

        Blockchain blockchain;

        try {
            blockchain = blockchainService.findById(event.getBlockchainId());
        } catch (ResourceNotFoundException e) {
            throw new BlockchainNotFoundException();
        }

        SmartContract smartContract;

        try {
            smartContract = smartContractService.findById(event.getSmartContractId());
        } catch (ResourceNotFoundException e) {
            throw new SmartContractNotFoundException();
        }

        if (blockchain.getPlatform() != smartContract.getBlockchainPlatform()) {
            throw new InvalidBlockchainPlatformException();
        }

        SmartContractClauseDto clause = smartContract
            .getClauses()
            .stream()
            .filter(item -> item.getName().equals(event.getClauseName()))
            .findFirst()
            .orElseThrow(InvalidSmartContractClauseException::new);

        if (!event.getClauseArguments().isEmpty()) {
            Set<String> argumentNames = clause.getClauseArguments().stream().map(SmartContractClauseArgumentDto::getName).collect(Collectors.toSet());

            boolean hasInvalidClauseArgument = event.getClauseArguments().stream().anyMatch(item -> !argumentNames.contains(item.getName()));

            if (hasInvalidClauseArgument) {
                throw new InvalidSmartContractClauseArgumentException();
            }
        }

        var payload = SmartContractPayloadDto
                .builder()
                .id(smartContractExecution.getId())
                .blockchain(blockchainMapper.toDto(blockchain))
                .smartContract(smartContractMapper.toDto(smartContract))
                .clauseName(event.getClauseName())
                .clauseArguments(event.getClauseArguments())
                .build();

        smartContractExecution.setStatus("QUEUED");
        smartContractExecution.setPayload(payload);
        smartContractExecutionService.update(smartContractExecution);

        amqpTemplate.convertAndSend("smart-contract-exchange", "smart-contract-execution-routing-key", payload);
    }

}
