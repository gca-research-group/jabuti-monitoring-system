package br.edu.unijui.gca.api.intake;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.intake.application.AcceptExecution;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceptExecutionTests {

    @Test
    void submissionPublishesTheExistingPayloadWithANewIdentifierAndUtcPublicationTimestamp() {
        var published = new ArrayList<SmartContractQueueInboundEventDto>();
        var intake = new AcceptExecution(published::add);
        var event = new SmartContractQueueInboundEventDto();
        UUID suppliedId = UUID.randomUUID();
        UUID blockchainId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        event.setId(suppliedId);
        event.setBlockchainId(blockchainId);
        event.setSmartContractId(contractId);
        event.setClauseName("recordReading");
        event.setClauseArguments(List.of());
        event.setMetadata(Map.of("sensor", "temperature"));
        event.setTimestamps(new HashMap<>(Map.of(SmartContractExecutionEvent.INBOUND_QUEUE_CONSUMED, "old")));
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

        UUID acceptedId = intake.execute(event);

        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);
        assertThat(acceptedId).isNotNull().isNotEqualTo(suppliedId);
        assertThat(published).singleElement().satisfies(message -> {
            assertThat(message.getId()).isEqualTo(acceptedId);
            assertThat(message.getBlockchainId()).isEqualTo(blockchainId);
            assertThat(message.getSmartContractId()).isEqualTo(contractId);
            assertThat(message.getClauseName()).isEqualTo("recordReading");
            assertThat(message.getClauseArguments()).isEmpty();
            assertThat(message.getMetadata()).containsExactlyEntriesOf(Map.of("sensor", "temperature"));
            assertThat(message.getTimestamps()).containsOnlyKeys(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED);
            OffsetDateTime publishedAt = OffsetDateTime.parse(message.getTimestamps()
                    .get(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED));
            assertThat(publishedAt).isBetween(before, after);
            assertThat(publishedAt.getOffset()).isEqualTo(ZoneOffset.UTC);
        });
    }

    @Test
    void publicationFailureIsPropagatedInsteadOfReturningAnAcceptedIdentifier() {
        var failure = new IllegalStateException("publisher unavailable");
        var intake = new AcceptExecution(event -> { throw failure; });

        assertThatThrownBy(() -> intake.execute(new SmartContractQueueInboundEventDto())).isSameAs(failure);
    }
}
