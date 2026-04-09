package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.services.BenchmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/benchmark")
public class BenchmarkResource {
    @Autowired
    private BenchmarkService service;

    @GetMapping("/start")
    public void start() {
        service.start();
    }

    @GetMapping("/stop")
    public void stop() {
        service.stop();
    }
}
