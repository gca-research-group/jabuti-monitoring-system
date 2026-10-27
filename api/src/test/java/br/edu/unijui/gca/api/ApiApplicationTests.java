package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.dtos.HyperledgerFabricConfigDto;
import br.edu.unijui.gca.api.dtos.PostExecutionActionDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseArgumentDto;
import br.edu.unijui.gca.api.dtos.SmartContractClauseDto;
import br.edu.unijui.gca.api.dtos.SmartContractPayloadDto;
import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.smartcontractexecution.SmartContractExecutionDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import br.edu.unijui.gca.api.enums.BlockchainPlatform;
import br.edu.unijui.gca.api.enums.PostExecutionActionType;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.enums.SmartContractExecutionStatus;
import br.edu.unijui.gca.api.exceptions.InvalidBlockchainPlatformException;
import br.edu.unijui.gca.api.execution.adapters.rabbitmq.ContractExecutionListener;
import br.edu.unijui.gca.api.execution.adapters.blockchain.BlockchainConnectionFactory;
import br.edu.unijui.gca.api.identity.adapters.http.AuthFilter;
import br.edu.unijui.gca.api.interfaces.IBlockchainConnection;
import br.edu.unijui.gca.api.postexecution.adapters.rabbitmq.PostExecutionListener;
import br.edu.unijui.gca.api.postexecution.application.ports.WebhookDelivery;
import br.edu.unijui.gca.api.preparation.adapters.rabbitmq.ExecutionPreparationListener;
import br.edu.unijui.gca.api.repositories.*;
import br.edu.unijui.gca.api.identity.adapters.security.JwtService;
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

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(strings = {"success", "invocation-failure", "publication-failure", "bad-config"})
    void executionAdaptersPreserveInvocationConversionOutcomesAndRejection(String scenario) {
        var factory = mock(BlockchainConnectionFactory.class);
        var connection = (IBlockchainConnection<Object, HyperledgerFabricConfigDto>) mock(IBlockchainConnection.class);
        doReturn(HyperledgerFabricConfigDto.class).when(factory).getConfigType(BlockchainPlatform.HYPERLEDGER_FABRIC);
        when(factory.<Object, HyperledgerFabricConfigDto>getInstance(BlockchainPlatform.HYPERLEDGER_FABRIC)).thenReturn(connection);
        runner.withPropertyValues("spring.profiles.active=execution")
                .withBean(BlockchainConnectionFactory.class, () -> factory).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BlockchainConnectionFactory.class)).isSameAs(factory);
            var payload = SmartContractPayloadDto.builder().id(UUID.randomUUID())
                    .blockchainId(UUID.randomUUID()).smartContractId(UUID.randomUUID()).clauseName("recordReading")
                    .clauseArguments(List.of(new SmartContractClauseArgumentDto("reading", "42"))).build();
            var execution = new SmartContractExecution();
            execution.setId(payload.getId());
            execution.setPayload(payload);
            execution.setStatus(SmartContractExecutionStatus.PROCESSING);
            execution.setTimestamps(new HashMap<>());
            var blockchain = new Blockchain();
            blockchain.setId(payload.getBlockchainId());
            blockchain.setPlatform(BlockchainPlatform.HYPERLEDGER_FABRIC);
            blockchain.setParameters(Map.of("mspId", scenario.equals("bad-config") ? Map.of("invalid", true) : "Org1MSP",
                    "channelName", "mychannel"));
            var contract = new SmartContract();
            contract.setId(payload.getSmartContractId());
            contract.setName("temperature-contract");
            var executions = context.getBean(SmartContractExecutionRepository.class);
            var contracts = context.getBean(SmartContractRepository.class);
            when(executions.findById(payload.getId())).thenReturn(Optional.of(execution));
            when(executions.save(any(SmartContractExecution.class))).thenAnswer(call -> call.getArgument(0));
            when(context.getBean(BlockchainRepository.class).findById(payload.getBlockchainId())).thenReturn(Optional.of(blockchain));
            when(contracts.findById(payload.getSmartContractId())).thenReturn(Optional.of(contract));
            when(connection.invoke(eq(payload.getBlockchainId()), any(HyperledgerFabricConfigDto.class),
                    eq("temperature-contract"), eq("recordReading"), eq(payload.getClauseArguments()))).thenReturn("receipt-42");
            var publisher = context.getBean(RabbitTemplate.class);
            if (scenario.equals("invocation-failure")) {
                when(connection.invoke(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("invocation failed"));
            } else if (scenario.equals("publication-failure")) {
                doThrow(new IllegalStateException("outcome publication failed")).doNothing().when(publisher)
                        .convertAndSend(eq("smart-contract.exchange"), eq("smart-contract.outbound.routing-key"), any(Object.class));
            }
            var listener = context.getBean(ContractExecutionListener.class);

            if (scenario.equals("success")) {
                listener.process(payload);
            } else {
                assertThatThrownBy(() -> listener.process(payload)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
            }

            var outcomes = ArgumentCaptor.forClass(Object.class);
            verify(publisher, times(scenario.equals("publication-failure") ? 2 : 1)).convertAndSend(
                    eq("smart-contract.exchange"), eq("smart-contract.outbound.routing-key"), outcomes.capture());
            var lastOutcome = (SmartContractExecutionDto) outcomes.getValue();
            assertThat(lastOutcome.getId()).isEqualTo(payload.getId());
            assertThat(lastOutcome.getPayload().getClauseArguments()).isEqualTo(payload.getClauseArguments());
            assertThat(lastOutcome.getStatus()).isEqualTo(scenario.equals("success")
                    ? SmartContractExecutionStatus.PROCESSED : SmartContractExecutionStatus.FAILED);
            if (scenario.equals("success")) {
                assertThat(lastOutcome.getResult()).isEqualTo("receipt-42");
            } else if (scenario.equals("publication-failure")) {
                assertThat(((SmartContractExecutionDto) outcomes.getAllValues().getFirst()).getStatus())
                        .isEqualTo(SmartContractExecutionStatus.PROCESSED);
                assertThat(lastOutcome.getResult()).isEqualTo("outcome publication failed");
            } else if (scenario.equals("invocation-failure")) {
                assertThat(lastOutcome.getResult()).isEqualTo("invocation failed");
            }
            if (scenario.equals("bad-config")) {
                verifyNoInteractions(connection);
                verify(contracts, never()).findById(any());
                verify(factory, never()).getInstance(any());
            } else {
                var config = ArgumentCaptor.forClass(HyperledgerFabricConfigDto.class);
                verify(connection).invoke(eq(payload.getBlockchainId()), config.capture(), eq("temperature-contract"),
                        eq("recordReading"), eq(payload.getClauseArguments()));
                assertThat(config.getValue().getMspId()).isEqualTo("Org1MSP");
                assertThat(config.getValue().getChannelName()).isEqualTo("mychannel");
            }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postExecutionAdaptersPreserveCompletionAndDeliveryFailure(boolean deliveryFails) {
        var delivery = mock(WebhookDelivery.class);
        runner.withPropertyValues("spring.profiles.active=post-execution")
                .withBean("testWebhookDelivery", WebhookDelivery.class, () -> delivery, definition -> definition.setPrimary(true))
                .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(WebhookDelivery.class)).isSameAs(delivery);
            var payload = SmartContractPayloadDto.builder().smartContractId(UUID.randomUUID()).clauseName("recordReading").build();
            var execution = new SmartContractExecution();
            execution.setId(UUID.randomUUID());
            execution.setPayload(payload);
            execution.setStatus(SmartContractExecutionStatus.PROCESSED);
            execution.setTimestamps(new HashMap<>());
            var contract = new SmartContract();
            contract.setClauses(List.of(new SmartContractClauseDto("recordReading", List.of(), List.of(
                    PostExecutionActionDto.builder().type(PostExecutionActionType.WEBHOOK).url("https://example.test/webhook").build()))));
            var executions = context.getBean(SmartContractExecutionRepository.class);
            when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
            when(executions.save(any(SmartContractExecution.class))).thenAnswer(call -> call.getArgument(0));
            when(context.getBean(SmartContractRepository.class).findById(payload.getSmartContractId())).thenReturn(Optional.of(contract));
            var event = SmartContractExecutionDto.builder().id(execution.getId()).payload(payload).build();
            var listener = context.getBean(PostExecutionListener.class);

            if (deliveryFails) {
                var failure = new IllegalStateException("delivery failed");
                doThrow(failure).when(delivery).deliver("https://example.test/webhook");
                assertThatThrownBy(() -> listener.process(event)).isSameAs(failure);
                verify(executions, never()).save(any(SmartContractExecution.class));
                assertThat(execution.getTimestamps()).doesNotContainKey(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED);
            } else {
                listener.process(event);
                verify(executions).save(execution);
                assertThat(execution.getTimestamps()).containsKey(SmartContractExecutionEvent.OUTBOUND_QUEUE_PROCESSED);
            }
            verify(delivery).deliver("https://example.test/webhook");
            assertThat(execution.getStatus()).isEqualTo(SmartContractExecutionStatus.PROCESSED);
        });
    }

}
