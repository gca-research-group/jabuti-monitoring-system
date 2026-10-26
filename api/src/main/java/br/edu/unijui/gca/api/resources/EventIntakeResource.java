package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.SmartContractQueueInboundEventDto;
import br.edu.unijui.gca.api.services.SmartContractExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@Profile("intake")
@RestController
@RequestMapping("/smart-contract-execution")
public class EventIntakeResource {

    private final SmartContractExecutionService service;

    @PostMapping("/execute")
    public UUID execute(@RequestBody SmartContractQueueInboundEventDto dto) {
        return service.execute(dto);
    }
}
