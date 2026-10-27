package br.edu.unijui.gca.api.intake.application;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.enums.SmartContractExecutionEvent;
import br.edu.unijui.gca.api.intake.application.ports.InboundEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.UUID;

public class AcceptExecution {

    private final InboundEventPublisher publisher;

    public AcceptExecution(InboundEventPublisher publisher) {
        this.publisher = publisher;
    }

    public UUID execute(SmartContractQueueInboundEventDto event) {
        var id = UUID.ofEpochMillis(System.currentTimeMillis());
        event.setId(id);

        var timestamps = new HashMap<SmartContractExecutionEvent, String>();
        timestamps.put(SmartContractExecutionEvent.INBOUND_QUEUE_PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC).toString());
        event.setTimestamps(timestamps);

        publisher.publish(event);
        return id;
    }
}
