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

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EndpointProfileTests {

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
