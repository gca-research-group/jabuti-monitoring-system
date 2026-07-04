package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.apikey.ApiKeyDto;
import br.edu.unijui.gca.api.services.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-key")
public class ApiKeyResource {

    private final ApiKeyService service;

    @PostMapping
    public ApiKeyDto create(@RequestBody ApiKeyDto request) {
        return service.create(request.userId());
    }
}
