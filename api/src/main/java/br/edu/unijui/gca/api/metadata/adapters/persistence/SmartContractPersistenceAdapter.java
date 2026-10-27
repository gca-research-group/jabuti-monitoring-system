package br.edu.unijui.gca.api.metadata.adapters.persistence;

import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import br.edu.unijui.gca.api.metadata.application.ports.SmartContractStore;
import br.edu.unijui.gca.api.repositories.SmartContractRepository;
import br.edu.unijui.gca.api.specifications.SmartContractSpecification;
import br.edu.unijui.gca.api.shared.adapters.persistence.BasePersistenceAdapter;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@RequiredArgsConstructor
public class SmartContractPersistenceAdapter extends BasePersistenceAdapter<SmartContract, UUID, SmartContractFilterDto> implements SmartContractStore {
    private final SmartContractRepository repository;
    private final SmartContractSpecification specification;

    @Override
    protected SmartContractRepository repository() {
        return repository;
    }

    @Override
    protected SmartContractSpecification specification() {
        return specification;
    }
}
