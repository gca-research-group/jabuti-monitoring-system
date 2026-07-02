package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.smartcontract.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import org.springframework.stereotype.Component;


@Component
public class SmartContractSpecification extends BaseSpecification<SmartContract, SmartContractFilterDto> {
    @Override
    protected Class<SmartContractFilterDto> getFilterClass() {
        return SmartContractFilterDto.class;
    }
}
