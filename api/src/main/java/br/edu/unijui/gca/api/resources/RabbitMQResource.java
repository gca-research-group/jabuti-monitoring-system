package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.services.RabbitMQService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController()
@RequestMapping("/rabbitmq")
public class RabbitMQResource {
    private final RabbitMQService service;

    @PostMapping("/start")
    public void start() {
        service.start();
    }

    @PostMapping("/stop")
    public void stop() {
        service.stop();
    }

    @PostMapping("/purge-all")
    public void purgeAll() {
        service.purgeAll();
    }

    @PostMapping("/consumers/{quantity}")
    public void consumers(@PathVariable int quantity) {
        service.consumers(quantity);
    }
}
