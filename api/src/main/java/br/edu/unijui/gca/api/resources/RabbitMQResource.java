package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.services.RabbitMQService;
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
    private final RabbitMQService service;

    @PostMapping("/purge-all")
    public void purgeAll() {
        service.purgeAll();
    }

}
