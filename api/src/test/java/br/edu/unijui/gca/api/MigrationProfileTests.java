package br.edu.unijui.gca.api;

import br.edu.unijui.gca.api.config.LiquibaseConfig;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MigrationProfileTests {

    private final DataSource dataSource = mock(DataSource.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(LiquibaseConfig.class)
            .withBean(DataSource.class, () -> dataSource)
            .withBean(BeanPostProcessor.class, () -> new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof SpringLiquibase liquibase) {
                        // Check selection of the real migration bean without running a migration.
                        liquibase.setShouldRun(false);
                    }
                    return bean;
                }
            });

    @ParameterizedTest
    @ValueSource(strings = {"intake", "preparation", "execution", "post-execution", "prod,preparation,execution,post-execution"})
    void rolesWithoutMetadataManagementDoNotOwnMigrations(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SpringLiquibase.class);
            verifyNoInteractions(dataSource);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"metadata-management", "prod,metadata-management", "metadata-management,prod",
            "prod,intake,preparation,execution,post-execution,metadata-management"})
    void metadataManagementOwnsOneMigrationBeanRegardlessOfProfileOrder(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SpringLiquibase.class);
            verifyNoInteractions(dataSource);
        });
    }

    @Test
    void explicitlyDisablingMigrationsIsRespected() {
        runner.withPropertyValues("spring.profiles.active=metadata-management", "spring.liquibase.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(SpringLiquibase.class);
                    verifyNoInteractions(dataSource);
                });
    }
}
