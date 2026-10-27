package br.edu.unijui.gca.api.intake;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.intake.application.AcceptExecution;
import br.edu.unijui.gca.api.intake.adapters.http.EventIntakeResource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntakeHttpTests {

    @Test
    void httpSubmissionNeedsOnlyTheIntakeUseCaseAndPreservesItsResponseAndPayload() {
        var published = new ArrayList<SmartContractQueueInboundEventDto>();
        new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=intake")
                .withUserConfiguration(HttpEndpoint.class)
                .withBean(AcceptExecution.class, () -> new AcceptExecution(published::add))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                    var response = mvc.perform(post("/smart-contract-execution/execute")
                                    .contentType(MediaType.APPLICATION_JSON).content("""
                                            {
                                              "blockchainId": "00000000-0000-0000-0000-000000000001",
                                              "smartContractId": "00000000-0000-0000-0000-000000000002",
                                              "clauseName": "recordReading",
                                              "clauseArguments": [{"name": "reading", "value": "42"}],
                                              "metadata": {"sensor": "temperature"}
                                            }
                                            """))
                            .andExpect(status().isOk()).andReturn();
                    UUID responseId = new ObjectMapper().readValue(response.getResponse().getContentAsString(), UUID.class);
                    assertThat(published).singleElement().satisfies(event -> {
                        assertThat(event.getId()).isEqualTo(responseId);
                        assertThat(event.getBlockchainId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
                        assertThat(event.getSmartContractId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
                        assertThat(event.getClauseName()).isEqualTo("recordReading");
                        assertThat(event.getClauseArguments()).singleElement().satisfies(argument -> {
                            assertThat(argument.getName()).isEqualTo("reading");
                            assertThat(argument.getValue()).isEqualTo("42");
                        });
                        assertThat(event.getMetadata()).containsEntry("sensor", "temperature");
                    });
                });
    }

    @EnableWebMvc
    @Import(EventIntakeResource.class)
    static class HttpEndpoint {
    }
}
