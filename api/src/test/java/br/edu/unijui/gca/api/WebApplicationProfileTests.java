package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.dtos.AuthResponseDto;
import br.edu.unijui.gca.api.dtos.apikey.ApiKeyDto;
import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.entities.User;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;
import br.edu.unijui.gca.api.identity.application.ports.ApiKeyStore;
import br.edu.unijui.gca.api.exceptions.ResourceNotFoundException;
import br.edu.unijui.gca.api.identity.adapters.http.AuthFilter;
import br.edu.unijui.gca.api.repositories.*;
import br.edu.unijui.gca.api.utils.HashUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.List;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebApplicationProfileTests {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ApiApplication.class)
            .withPropertyValues("spring.autoconfigure.exclude="
                            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                            + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                            + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
                    "spring.rabbitmq.listener.simple.auto-startup=false", "spring.liquibase.enabled=false",
                    "jwt.secret=local-profile-check-secret-at-least-32-bytes", "jwt.expiration=3600000",
                    "app.backend-url=http://localhost:8080")
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean("jpaMappingContext", JpaMetamodelMappingContext.class, () -> mock(JpaMetamodelMappingContext.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(ApiKeyRepository.class, () -> mock(ApiKeyRepository.class))
            .withBean(BlockchainRepository.class, () -> mock(BlockchainRepository.class))
            .withBean(SmartContractRepository.class, () -> mock(SmartContractRepository.class))
            .withBean(SmartContractExecutionRepository.class, () -> mock(SmartContractExecutionRepository.class));

    @ParameterizedTest
    @ValueSource(strings = {"intake", "metadata-management", "intake,metadata-management",
            "intake,preparation", "execution,metadata-management",
            "metadata-management,post-execution,execution,preparation,intake"})
    void webAndCombinedProfilesWireWithTheirSecurityDependencies(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AuthFilter.class);
            assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isEqualTo("servlet");
            var selected = Set.of(profiles.split(","));
            var mappings = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                    .getHandlerMethods();
            var paths = mappings.keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream())
                    .collect(Collectors.toSet());
            assertThat(paths.contains("/smart-contract-execution/execute")).isEqualTo(selected.contains("intake"));
            assertThat(paths.contains("/auth/login")).isEqualTo(selected.contains("metadata-management"));
            assertThat(paths).doesNotContain("/rabbitmq/start", "/rabbitmq/stop", "/rabbitmq/consumers/{quantity}");
            var registry = context.getBean(RabbitListenerEndpointRegistry.class);
            var expectedWorkers = selected.stream().filter(role -> Set.of("preparation", "execution", "post-execution").contains(role))
                    .collect(Collectors.toSet());
            assertThat(registry.getListenerContainerIds()).containsExactlyInAnyOrderElementsOf(expectedWorkers);
            assertThat(registry.getListenerContainers()).allSatisfy(container -> assertThat(container.isRunning()).isFalse());
            verify(context.getBean(ConnectionFactory.class), never()).createConnection();
        });
    }

    @Test
    void intakeStillAuthenticatesSubmissionBeforePublishing() {
        runner.withPropertyValues("spring.profiles.active=intake").run(context -> {
            assertThat(context).hasNotFailed();
            var mvc = mvc(context.getSourceApplicationContext());

            mvc.perform(post("/smart-contract-execution/execute")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            var publisher = context.getBean(RabbitTemplate.class);
            verify(publisher, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));

            var apiKey = new ApiKey();
            apiKey.setKeyHash(HashUtils.sha256("secret"));
            when(context.getBean(ApiKeyRepository.class).findByKeyPrefix("prefix"))
                    .thenReturn(Optional.of(apiKey));

            mvc.perform(post("/smart-contract-execution/execute").header("X-API-Key", "jms_prefix.wrong-secret")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            verify(publisher, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));

            var response = mvc.perform(post("/smart-contract-execution/execute").header("X-API-Key", "jms_prefix.secret")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk()).andReturn();
            var message = ArgumentCaptor.forClass(SmartContractQueueInboundEventDto.class);
            verify(publisher).convertAndSend(eq("smart-contract.exchange"), eq("smart-contract.inbound.routing-key"), message.capture());
            UUID responseId = new ObjectMapper().readValue(response.getResponse().getContentAsString(), UUID.class);
            assertThat(message.getValue().getId()).isEqualTo(responseId);
            verifyNoInteractions(context.getBean(SmartContractExecutionRepository.class));
        });
    }

    @Test
    void loginRefreshCookiesAndBearerProtectedManagementRetainTheirContracts() {
        runner.withPropertyValues("spring.profiles.active=metadata-management").run(context -> {
            assertThat(context).hasNotFailed();
            var user = existingUser(context.getSourceApplicationContext());
            var mvc = mvc(context.getSourceApplicationContext());
            var wrong = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"researcher@example.test\",\"password\":\"wrong\"}"))
                    .andExpect(status().isBadRequest()).andReturn();
            assertThat(wrong.getResponse().getCookie("jrt")).isNull();
            var login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"researcher@example.test\",\"password\":\"correct\"}"))
                    .andExpect(status().isOk()).andReturn();
            var body = new ObjectMapper().readValue(login.getResponse().getContentAsString(), AuthResponseDto.class);
            assertThat(body.id()).isEqualTo(7L);
            assertThat(body.email()).isEqualTo(user.getEmail());
            assertThat(body.accessToken()).isNotBlank();
            Cookie cookie = login.getResponse().getCookie("jrt");
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSecure()).isFalse();
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.getMaxAge()).isEqualTo(86400);
            var refreshed = mvc.perform(post("/auth/refresh").cookie(cookie)).andExpect(status().isOk()).andReturn();
            assertThat(refreshed.getResponse().getCookie("jrt")).isNotNull();
            mvc.perform(get("/user/7")).andExpect(status().isBadRequest());
            var managed = mvc.perform(get("/user/7").header("Authorization", "Bearer " + body.accessToken()))
                    .andExpect(status().isOk()).andReturn();
            assertThat(new ObjectMapper().readTree(managed.getResponse().getContentAsString()).path("name").asText())
                    .isEqualTo("Researcher");
        });
    }

    @Test
    void expiredRefreshRetainsItsCookieAndUnauthorizedResponse() {
        runner.withPropertyValues("spring.profiles.active=metadata-management", "jwt.expiration=-60000").run(context -> {
            assertThat(context).hasNotFailed();
            var user = existingUser(context.getSourceApplicationContext());
            String expired = context.getBean(TokenOperations.class).generateAccessToken(user);
            var result = mvc(context.getSourceApplicationContext()).perform(post("/auth/refresh").cookie(new Cookie("jrt", expired)))
                    .andExpect(status().isUnauthorized()).andReturn();
            Cookie cookie = result.getResponse().getCookie("jrt");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isNull();
            assertThat(cookie.getMaxAge()).isEqualTo(86400);
            assertThat(cookie.isHttpOnly()).isTrue();
        });
    }

    @Test
    void apiKeyCreationHashingAndCachingStillSupportIntakeInCombinedRoles() {
        runner.withPropertyValues("spring.profiles.active=metadata-management,intake", "spring.cache.type=simple").run(context -> {
            assertThat(context).hasNotFailed();
            var user = existingUser(context.getSourceApplicationContext());
            var mvc = mvc(context.getSourceApplicationContext());
            String accessToken = context.getBean(TokenOperations.class).generateAccessToken(user);
            var repository = context.getBean(ApiKeyRepository.class);
            when(repository.save(any(ApiKey.class))).thenAnswer(call -> call.getArgument(0));
            var created = mvc.perform(post("/api-key").header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":7}"))
                    .andExpect(status().isOk()).andReturn();
            var response = new ObjectMapper().readValue(created.getResponse().getContentAsString(), ApiKeyDto.class);
            assertThat(response.userId()).isEqualTo(7L);
            assertThat(response.keyHash()).matches("jms_[A-Za-z0-9_-]{8}\\.[A-Za-z0-9_-]{43}");
            String[] parts = response.keyHash().substring(4).split("\\.");
            var saved = ArgumentCaptor.forClass(ApiKey.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getUser()).isSameAs(user);
            assertThat(saved.getValue().getKeyPrefix()).isEqualTo(parts[0]);
            assertThat(saved.getValue().getKeyHash()).containsExactly(MessageDigest.getInstance("SHA-256")
                    .digest(parts[1].getBytes(StandardCharsets.UTF_8)));
            when(repository.findByKeyPrefix(parts[0])).thenReturn(Optional.of(saved.getValue()));
            for (int request = 0; request < 2; request++) {
                mvc.perform(post("/smart-contract-execution/execute").header("X-API-Key", response.keyHash())
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isOk());
            }
            verify(repository).findByKeyPrefix(parts[0]);
            when(repository.findByKeyPrefix("missing")).thenReturn(Optional.empty());
            for (int request = 0; request < 2; request++) {
                assertThatThrownBy(() -> context.getBean(ApiKeyStore.class).findByKeyPrefix("missing"))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
            verify(repository, times(2)).findByKeyPrefix("missing");
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void managementRetainsPaginationValidationAndContractUpdatePersistence() {
        runner.withPropertyValues("spring.profiles.active=metadata-management").run(context -> {
            assertThat(context).hasNotFailed();
            var user = existingUser(context.getSourceApplicationContext());
            var mvc = mvc(context.getSourceApplicationContext());
            String authorization = "Bearer " + context.getBean(TokenOperations.class).generateAccessToken(user);
            var query = mock(JpaSpecificationExecutor.SpecificationFluentQuery.class);
            when(query.slice(any(Pageable.class))).thenReturn(new SliceImpl<>(List.of(user), PageRequest.of(1, 2), true));
            when(context.getBean(UserRepository.class).findBy(any(Specification.class), any()))
                    .thenAnswer(call -> ((Function) call.getArgument(1)).apply(query));
            var listed = mvc.perform(get("/user").header("Authorization", authorization)
                            .param("page", "2").param("pageSize", "2").param("orderBy", "email").param("name", "Researcher"))
                    .andExpect(status().isOk()).andReturn();
            var list = new ObjectMapper().readTree(listed.getResponse().getContentAsString());
            assertThat(list.path("data").get(0).path("name").asText()).isEqualTo("Researcher");
            assertThat(list.path("hasMore").asBoolean()).isTrue();
            var pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(query).slice(pageable.capture());
            assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
            assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
            assertThat(pageable.getValue().getSort().getOrderFor("email")).isNotNull();
            mvc.perform(get("/user").header("Authorization", authorization).param("page", "0"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/smart-contract").header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"blockchainPlatform\":\"HYPERLEDGER_FABRIC\",\"status\":true,\"clauses\":[]}"))
                    .andExpect(status().isBadRequest());
            var contracts = context.getBean(SmartContractRepository.class);
            verify(contracts, never()).save(any());
            when(contracts.save(any(SmartContract.class))).thenAnswer(call -> call.getArgument(0));
            UUID contractId = UUID.randomUUID();
            var updated = mvc.perform(put("/smart-contract/" + contractId).header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"renamed\",\"blockchainPlatform\":\"HYPERLEDGER_FABRIC\",\"status\":true,\"clauses\":[]}"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(new ObjectMapper().readTree(updated.getResponse().getContentAsString()).path("id").asText())
                    .isEqualTo(contractId.toString());
            verify(contracts, never()).findById(any());
            var saved = ArgumentCaptor.forClass(SmartContract.class);
            verify(contracts).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("renamed");
        });
    }

    private MockMvc mvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class), context.getBean(AuthFilter.class)).build();
    }

    private User existingUser(WebApplicationContext context) {
        var user = new User();
        user.setId(7L);
        user.setName("Researcher");
        user.setEmail("researcher@example.test");
        user.setPassword(context.getBean(PasswordEncoder.class).encode("correct"));
        when(context.getBean(UserRepository.class).findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(context.getBean(UserRepository.class).findById(7L)).thenReturn(Optional.of(user));
        return user;
    }
}
