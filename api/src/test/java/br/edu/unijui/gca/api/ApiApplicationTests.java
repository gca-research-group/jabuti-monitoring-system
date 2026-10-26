package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.filters.AuthFilter;
import br.edu.unijui.gca.api.repositories.*;
import br.edu.unijui.gca.api.services.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

}
