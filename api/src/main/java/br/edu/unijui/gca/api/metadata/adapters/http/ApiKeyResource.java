package br.edu.unijui.gca.api.metadata.adapters.http;

import br.edu.unijui.gca.api.dtos.apikey.ApiKeyDto;
import br.edu.unijui.gca.api.identity.application.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Profile("metadata-management")
@RestController
@RequestMapping("/api-key")
public class ApiKeyResource {

    private final ApiKeyService service;

    @PostMapping
    public ApiKeyDto create(@RequestBody ApiKeyDto request) {
        return service.create(request.userId());
    }
}
