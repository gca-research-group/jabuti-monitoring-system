package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.SmartContractExecutionFilterDto;
import br.edu.unijui.gca.api.entities.SmartContractExecution;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SmartContractExecutionSpecification implements ISpecification<SmartContractExecution, SmartContractExecutionFilterDto> {
    @Override
    public Specification<SmartContractExecution> build(SmartContractExecutionFilterDto filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), filter.getId()));
            }

            if (filter.getStatus() != null && !filter.getStatus().equals("ALL")) {
                predicates.add(criteriaBuilder.like(root.get("status"), filter.getStatus()));
            }

            if (filter.getBlockchainPlatform() != null) {
                Expression<String> expression =
                        criteriaBuilder.function(
                            "jsonb_extract_path_text",
                            String.class,
                            root.get("payload"),
                            criteriaBuilder.literal("blockchain"),
                            criteriaBuilder.literal("platform")
                        );

                predicates.add(criteriaBuilder.equal(
                        expression,
                        filter.getBlockchainPlatform()
                ));
            }

            if (filter.getSmartContractName() != null) {
                Expression<String> expression =
                        criteriaBuilder.function(
                                "jsonb_extract_path_text",
                                String.class,
                                root.get("payload"),
                                criteriaBuilder.literal("smartContract"),
                                criteriaBuilder.literal("name")
                        );

                predicates.add(criteriaBuilder.like(
                        expression,
                        filter.getSmartContractName()
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
