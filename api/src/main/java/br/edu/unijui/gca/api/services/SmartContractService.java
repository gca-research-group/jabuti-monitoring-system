package br.edu.unijui.gca.api.services;

import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractDto;
import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.mappers.SmartContractMapper;
import br.edu.unijui.gca.api.repositories.SmartContractRepository;
import br.edu.unijui.gca.api.specifications.SmartContractSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class SmartContractService extends BaseService<
        SmartContract,
        UUID,
        SmartContractFilterDto,
        SmartContractDto> {

    private final SmartContractRepository repository;

    private final SmartContractSpecification specification;

    private final SmartContractMapper mapper;

    @Override
    protected SmartContractRepository repository() {
        return repository;
    }

    @Override
    protected SmartContractSpecification specification() {
        return specification;
    }

    @Override
    protected SmartContractMapper mapper() {
        return mapper;
    }
}
