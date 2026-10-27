package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.exceptions.InvalidBlockchainPlatformException;
import br.edu.unijui.gca.api.filters.AuthFilter;
import br.edu.unijui.gca.api.preparation.adapters.rabbitmq.ExecutionPreparationListener;
import br.edu.unijui.gca.api.repositories.*;
import br.edu.unijui.gca.api.services.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ApiApplicationTests {


    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ApiApplication.class)
            .withPropertyValues("spring.autoconfigure.exclude="
                            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                            + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
                    "spring.rabbitmq.listener.simple.auto-startup=false")
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean("jpaMappingContext", JpaMetamodelMappingContext.class, () -> mock(JpaMetamodelMappingContext.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(ApiKeyRepository.class, () -> mock(ApiKeyRepository.class))
            .withBean(BlockchainRepository.class, () -> mock(BlockchainRepository.class))
            .withBean(SmartContractRepository.class, () -> mock(SmartContractRepository.class))
            .withBean(SmartContractExecutionRepository.class, () -> mock(SmartContractExecutionRepository.class));

    @ParameterizedTest
    @ValueSource(strings = {"preparation", "execution", "post-execution", "preparation,execution,post-execution"})
    void workersWireWithoutWebCredentialsOrBackendUrl(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AuthFilter.class).doesNotHaveBean(JwtService.class);
            assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isEqualTo("none");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preparationAdaptersPreservePersistenceMappingRoutingAndRejection(boolean invalidPlatform) {
        runner.withPropertyValues("spring.profiles.active=preparation").run(context -> {
            assertThat(context).hasNotFailed();
            var event = new SmartContractQueueInboundEventDto();
            event.setId(UUID.randomUUID());
            event.setBlockchainId(UUID.randomUUID());
            event.setSmartContractId(UUID.randomUUID());
            event.setClauseName("recordReading");
            event.setClauseArguments(List.of(new SmartContractClauseArgumentDto("reading", "42")));
            event.setMetadata(Map.of("sensor", "temperature"));
            event.setTimestamps(new HashMap<>());
            var blockchain = new Blockchain();
            blockchain.setId(event.getBlockchainId());
            blockchain.setPlatform(BlockchainPlatform.HYPERLEDGER_FABRIC);
            var contract = new SmartContract();
            contract.setId(event.getSmartContractId());
            contract.setBlockchainPlatform(invalidPlatform ? BlockchainPlatform.DUMMY : BlockchainPlatform.HYPERLEDGER_FABRIC);
            contract.setClauses(List.of(new SmartContractClauseDto("recordReading",
                    List.of(new SmartContractClauseArgumentDto("reading", null)), List.of())));
            var executions = context.getBean(SmartContractExecutionRepository.class);
            when(executions.save(any(SmartContractExecution.class))).thenAnswer(call -> call.getArgument(0));
            when(context.getBean(BlockchainRepository.class).findById(event.getBlockchainId())).thenReturn(Optional.of(blockchain));
            when(context.getBean(SmartContractRepository.class).findById(event.getSmartContractId())).thenReturn(Optional.of(contract));
            var listener = context.getBean(ExecutionPreparationListener.class);

            if (invalidPlatform) {
                assertThatThrownBy(() -> listener.process(event)).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                        .hasCauseInstanceOf(InvalidBlockchainPlatformException.class);
            } else {
                listener.process(event);
            }

            var published = ArgumentCaptor.forClass(Object.class);
            verify(context.getBean(RabbitTemplate.class)).convertAndSend(eq("smart-contract.exchange"),
                    eq(invalidPlatform ? "smart-contract.outbound.routing-key" : "smart-contract.execution.routing-key"),
                    published.capture());
            if (invalidPlatform) {
                var outcome = (SmartContractExecutionDto) published.getValue();
                assertThat(outcome.getId()).isEqualTo(event.getId());
                assertThat(outcome.getStatus()).isEqualTo(SmartContractExecutionStatus.FAILED);
                assertThat(outcome.getResult()).isEqualTo("INVALID_BLOCKCHAIN_PLATFORM");
                assertThat(outcome.getMetadata()).isEqualTo(event.getMetadata());
            } else {
                var payload = (SmartContractPayloadDto) published.getValue();
                assertThat(payload.getId()).isEqualTo(event.getId());
                assertThat(payload.getBlockchainId()).isEqualTo(event.getBlockchainId());
                assertThat(payload.getSmartContractId()).isEqualTo(event.getSmartContractId());
                assertThat(payload.getClauseArguments()).isEqualTo(event.getClauseArguments());
            }
            verify(executions, times(3)).save(any(SmartContractExecution.class));
        });
    }

}
