package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.services.BenchmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/benchmark")
public class BenchmarkResource {
    @Autowired
    private BenchmarkService service;

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
