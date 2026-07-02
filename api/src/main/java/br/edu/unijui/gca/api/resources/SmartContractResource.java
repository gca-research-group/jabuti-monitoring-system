package br.edu.unijui.gca.api.resources;

import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractDto;
import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import br.edu.unijui.gca.api.services.SmartContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController()
@RequestMapping("/smart-contract")
public class SmartContractResource extends BaseResource<SmartContract, UUID, SmartContractFilterDto, SmartContractDto> {

    private final SmartContractMapper mapper;

    private final SmartContractService service;

    @Override
    protected SmartContractMapper mapper() {
        return mapper;
    }

    @Override
    protected SmartContractService service() {
        return service;
    }
}
