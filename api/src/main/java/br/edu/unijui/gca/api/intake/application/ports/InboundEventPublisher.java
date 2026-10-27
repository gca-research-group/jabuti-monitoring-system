package br.edu.unijui.gca.api.intake.application.ports;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;

public interface InboundEventPublisher {
    void publish(SmartContractQueueInboundEventDto event);
}
