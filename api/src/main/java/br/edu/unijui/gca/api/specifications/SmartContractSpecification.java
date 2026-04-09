package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SmartContractSpecification implements ISpecification<SmartContract, SmartContractFilterDto> {
    @Override
    public Specification<SmartContract> build(SmartContractFilterDto filter) {
        return null;
    }
}
