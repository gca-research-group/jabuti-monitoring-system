package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.UserFilterDto;
import br.edu.unijui.gca.api.entities.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class UserSpecification  implements ISpecification<User, UserFilterDto> {
    @Override
    public Specification<User> build(UserFilterDto filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getName() != null) {
                predicates.add(criteriaBuilder.like(root.get("name"), filter.getName()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
