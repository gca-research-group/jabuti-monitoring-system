package br.edu.unijui.gca.api.metadata.adapters.http;

import br.edu.unijui.gca.api.metadata.application.ports.QueueMaintenance;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Profile("metadata-management")
@RestController()
@RequestMapping("/rabbitmq")
public class RabbitMQResource {
    private final QueueMaintenance service;

    @PostMapping("/purge-all")
    public void purgeAll() {
        service.purgeAll();
    }

}
