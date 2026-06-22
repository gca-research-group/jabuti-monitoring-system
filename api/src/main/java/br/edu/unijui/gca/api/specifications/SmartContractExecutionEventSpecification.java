package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionEventFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecutionEvent;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class SmartContractExecutionEventSpecification  implements ISpecification<SmartContractExecutionEvent, SmartContractExecutionEventFilterDto>{
    @Override
    public Specification<SmartContractExecutionEvent> build(SmartContractExecutionEventFilterDto filter) {
        return null;
    }
}
