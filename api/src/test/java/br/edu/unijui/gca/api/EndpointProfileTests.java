package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.intake.adapters.http.EventIntakeResource;
import br.edu.unijui.gca.api.intake.application.AcceptExecution;
import br.edu.unijui.gca.api.mappers.*;
import br.edu.unijui.gca.api.identity.adapters.http.AuthResource;
import br.edu.unijui.gca.api.identity.application.ports.TokenOperations;
import br.edu.unijui.gca.api.metadata.adapters.http.BlockchainResource;
import br.edu.unijui.gca.api.identity.application.*;
import br.edu.unijui.gca.api.metadata.application.*;
import br.edu.unijui.gca.api.metadata.application.ports.QueueMaintenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.edu.unijui.gca.api.dtos.blockchain.BlockchainFilterDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import br.edu.unijui.gca.api.interfaces.CrudMapper;
import br.edu.unijui.gca.api.metadata.adapters.http.CrudResource;
import br.edu.unijui.gca.api.metadata.application.ports.BlockchainStore;
import br.edu.unijui.gca.api.shared.application.CrudService;
import br.edu.unijui.gca.api.shared.application.UpdateField;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EndpointProfileTests {

    @Test
    void blockchainCrudPreservesItsHttpContractThroughTheExpandedBoundaries() {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var store = mock(BlockchainStore.class);
        var mapper = Mappers.getMapper(BlockchainMapper.class);
        var saved = new AtomicReference<Blockchain>();
        when(store.save(any())).thenAnswer(call -> {
            Blockchain entity = call.getArgument(0);
            entity.setId(id);
            saved.set(entity);
            return entity;
        });
        when(store.findById(id)).thenAnswer(call -> saved.get());
        when(store.findAll(any(), any()))
                .thenAnswer(call -> new SliceImpl<>(List.of(saved.get())));
        new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=metadata-management", "app.backend-url=http://localhost:8080")
                .withUserConfiguration(BlockchainEndpoint.class)
                .withBean(BlockchainService.class, () -> new BlockchainService(store, mapper))
                .withBean(BlockchainMapper.class, () -> mapper)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var mvc = MockMvcBuilders
                            .webAppContextSetup(context.getSourceApplicationContext()).build();
                    var responseMapper = new ObjectMapper();
                    var created = mvc.perform(
                                    post("/blockchain").contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"name":"Ledger","platform":"DUMMY","status":true,
                                             "remarks":"original","parameters":{"network":"local"}}
                                            """))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    var json = responseMapper.readTree(created);
                    assertThat(json.get("id").asText()).isEqualTo(id.toString());
                    assertThat(json.get("name").asText()).isEqualTo("Ledger");
                    assertThat(json.get("parameters").get("network").asText()).isEqualTo("local");
                    var updated = mvc.perform(
                                    put("/blockchain/{id}", id).contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"id":"00000000-0000-0000-0000-000000000002",
                                             "name":"Renamed","platform":"DUMMY","remarks":null}
                                            """))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    json = responseMapper.readTree(updated);
                    assertThat(json.get("id").asText()).isEqualTo(id.toString());
                    assertThat(json.get("name").asText()).isEqualTo("Renamed");
                    assertThat(json.get("remarks").isNull()).isTrue();
                    assertThat(json.get("parameters").isNull()).isTrue();
                    assertThat(json.get("status").asBoolean()).isFalse();
                    mvc.perform(get("/blockchain/{id}", id))
                            .andExpect(status().isOk());
                    var listed = mvc.perform(get("/blockchain"))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString();
                    assertThat(responseMapper.readTree(listed).get("data").get(0).get("name").asText()).isEqualTo("Renamed");
                    mvc.perform(delete("/blockchain/{id}", id))
                            .andExpect(status().isOk());
                    verify(store).remove(id);
                    mvc.perform(post("/blockchain")
                                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isBadRequest());
                });
    }

    @EnableWebMvc
    @Import(BlockchainResource.class)
    static class BlockchainEndpoint {
    }

    @Test
    void independentContractsPreserveAbsentFieldsAndAllowExplicitNullThroughHttp() {
        var entity = new Blockchain();
        var store = mock(BlockchainStore.class);
        when(store.findById(any())).thenReturn(entity);
        when(store.save(any())).thenAnswer(call -> call.getArgument(0));
        var mapper = new CrudMapper<Blockchain, CreateName, UpdateRemarks, NameResponse>() {
            public Blockchain toEntity(CreateName request) {
                entity.setName(request.name());
                entity.setRemarks("initial");
                return entity;
            }
            public void updateEntity(UpdateRemarks request, Blockchain target) {
                request.remarks.ifSupplied(target::setRemarks);
            }
            public NameResponse toDto(Blockchain target) {
                return new NameResponse(target.getName(), target.getRemarks());
            }
        };
        var service = new CrudService<Blockchain, UUID, BlockchainFilterDto, CreateName, UpdateRemarks, NameResponse>() {
            protected BlockchainStore store() { return store; }
            protected CrudMapper<Blockchain, CreateName, UpdateRemarks, NameResponse> mapper() { return mapper; }
        };
        new WebApplicationContextRunner().withPropertyValues("spring.profiles.active=crud-contract-test")
                .withUserConfiguration(IndependentEndpoint.class)
                .withBean(IndependentResource.class, () -> new IndependentResource(service, mapper))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                    var json = new ObjectMapper();
                    var created = mvc.perform(post("/independent").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"name\":\"Ledger\"}"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    assertThat(json.readTree(created).get("name").asText()).isEqualTo("Ledger");
                    var url = "/independent/00000000-0000-0000-0000-000000000001";
                    var omitted = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    assertThat(json.readTree(omitted).get("remarks").asText()).isEqualTo("initial");
                    var cleared = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"remarks\":null}"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    assertThat(json.readTree(cleared).get("remarks").isNull()).isTrue();
                    var replaced = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"remarks\":\"replacement\"}"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                    assertThat(json.readTree(replaced).get("remarks").asText()).isEqualTo("replacement");
                    assertThat(json.readTree(replaced).get("name").asText()).isEqualTo("Ledger");
                });
    }

    public record CreateName(String name) { }
    public record NameResponse(String name, String remarks) { }

    public static class UpdateRemarks {
        private UpdateField<String> remarks = UpdateField.absent();

        @JsonSetter("remarks")
        public void setRemarks(String value) {
            remarks = UpdateField.supplied(value);
        }
    }

    @RestController
    @org.springframework.context.annotation.Profile("crud-contract-test")
    @RequestMapping("/independent")
    static class IndependentResource extends CrudResource<Blockchain, UUID, BlockchainFilterDto, CreateName, UpdateRemarks, NameResponse> {
        private final CrudService<Blockchain, UUID, BlockchainFilterDto, CreateName, UpdateRemarks, NameResponse> service;
        private final CrudMapper<Blockchain, CreateName, UpdateRemarks, NameResponse> mapper;

        IndependentResource(CrudService<Blockchain, UUID, BlockchainFilterDto, CreateName, UpdateRemarks, NameResponse> service,
                            CrudMapper<Blockchain, CreateName, UpdateRemarks, NameResponse> mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        protected CrudService<Blockchain, UUID, BlockchainFilterDto, CreateName, UpdateRemarks, NameResponse> service() { return service; }
        protected CrudMapper<Blockchain, CreateName, UpdateRemarks, NameResponse> mapper() { return mapper; }
    }

    @EnableWebMvc
    static class IndependentEndpoint { }
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withPropertyValues("app.backend-url=http://localhost:8080")
            .withUserConfiguration(Endpoints.class)
            .withBean(AcceptExecution.class, () -> new AcceptExecution(event -> { }))
            .withBean(AuthService.class, () -> mock(AuthService.class))
            .withBean(TokenOperations.class, () -> mock(TokenOperations.class))
            .withBean(ApiKeyService.class, () -> mock(ApiKeyService.class))
            .withBean(UserService.class, () -> mock(UserService.class))
            .withBean(UserMapper.class, () -> mock(UserMapper.class))
            .withBean(BlockchainService.class, () -> mock(BlockchainService.class))
            .withBean(BlockchainMapper.class, () -> mock(BlockchainMapper.class))
            .withBean(SmartContractService.class, () -> mock(SmartContractService.class))
            .withBean(SmartContractMapper.class, () -> mock(SmartContractMapper.class))
            .withBean(SmartContractExecutionService.class, () -> mock(SmartContractExecutionService.class))
            .withBean(SmartContractExecutionMapper.class, () -> mock(SmartContractExecutionMapper.class))
            .withBean(QueueMaintenance.class, () -> mock(QueueMaintenance.class));

    @Test
    void intakeExposesSubmissionWithoutManagementEndpoints() {
        runner.withPropertyValues("spring.profiles.active=intake").run(context -> {
            assertThat(context).hasNotFailed();
            Set<String> paths = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods()
                    .keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream())
                    .collect(Collectors.toSet());
            assertThat(paths).containsExactly("/smart-contract-execution/execute");
        });
    }

    @Test
    void metadataManagementExposesManagementAndPurgeButNotSubmissionOrListenerControls() {
        runner.withPropertyValues("spring.profiles.active=metadata-management").run(context -> {
            assertThat(context).hasNotFailed();
            Set<String> paths = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods()
                    .keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream())
                    .collect(Collectors.toSet());
            assertThat(paths).contains("/auth/login", "/auth/refresh", "/user", "/api-key",
                            "/blockchain", "/smart-contract", "/smart-contract-execution",
                            "/smart-contract-execution/{id}", "/rabbitmq/purge-all")
                    .doesNotContain("/smart-contract-execution/execute", "/rabbitmq/start",
                            "/rabbitmq/stop", "/rabbitmq/consumers/{quantity}");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"preparation", "execution", "post-execution", "preparation,execution,post-execution"})
    void workersExposeNoApplicationHttpEndpoints(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods()).isEmpty();
        });
    }

    @Test
    void combinedWebRolesRegisterSubmissionAndManagementWithoutDuplicateMappings() {
        runner.withPropertyValues("spring.profiles.active=intake,metadata-management").run(context -> {
            assertThat(context).hasNotFailed();
            var mappings = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods();
            assertThat(mappings.keySet().stream()
                    .filter(mapping -> mapping.getPatternValues().contains("/smart-contract-execution/execute")))
                    .hasSize(1);
            assertThat(mappings.keySet().stream()
                    .filter(mapping -> mapping.getPatternValues().contains("/auth/login"))).hasSize(1);
        });
    }

    @EnableWebMvc
    @ComponentScan(basePackageClasses = {AuthResource.class, BlockchainResource.class, EventIntakeResource.class},
            useDefaultFilters = false, includeFilters = @ComponentScan.Filter(RestController.class))
    static class Endpoints {
    }
}
