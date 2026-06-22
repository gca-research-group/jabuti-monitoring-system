package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.SmartContractFilterDto;
import br.edu.unijui.gca.api.entities.SmartContract;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SmartContractSpecification implements ISpecification<SmartContract, SmartContractFilterDto> {
    @Override
    public Specification<SmartContract> build(SmartContractFilterDto filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getName() != null) {
                predicates.add(criteriaBuilder.like(root.get("name"), filter.getName()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
