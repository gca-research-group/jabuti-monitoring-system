package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.entities.ApiKey;
import br.edu.unijui.gca.api.filters.AuthFilter;
import br.edu.unijui.gca.api.repositories.*;
import br.edu.unijui.gca.api.utils.HashUtils;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            "metadata-management,post-execution,execution,preparation,intake"})
    void webAndCombinedProfilesWireWithTheirSecurityDependencies(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AuthFilter.class);
            assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isEqualTo("servlet");
        });
    }

    @Test
    void intakeStillAuthenticatesSubmissionBeforePublishing() {
        runner.withPropertyValues("spring.profiles.active=intake").run(context -> {
            assertThat(context).hasNotFailed();
            var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class), context.getBean(AuthFilter.class))
                    .build();

            mvc.perform(post("/smart-contract-execution/execute")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            var publisher = context.getBean(RabbitTemplate.class);
            verify(publisher, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));

            var apiKey = new ApiKey();
            apiKey.setKeyHash(HashUtils.sha256("secret"));
            when(context.getBean(ApiKeyRepository.class).findByKeyPrefix("prefix"))
                    .thenReturn(Optional.of(apiKey));
            mvc.perform(post("/smart-contract-execution/execute").header("X-API-Key", "jms_prefix.secret")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
            verify(publisher).convertAndSend(eq("smart-contract.exchange"), eq("smart-contract.inbound.routing-key"), any(Object.class));
        });
    }
}
