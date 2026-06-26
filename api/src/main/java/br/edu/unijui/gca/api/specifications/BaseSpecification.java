package br.edu.unijui.gca.api.specifications;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseSpecification<E, F> implements ISpecification<E, F> {

    public Specification<E> build(F filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (Field field : getFilterClass().getDeclaredFields()) {
                try {
                    if (shouldIgnore(field)) {
                        continue;
                    }

                    field.setAccessible(true);
                    Object value = field.get(filter);

                    if (value != null) {
                        predicates.add(buildPredicate(root, criteriaBuilder, field, value));
                    }

                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected abstract Class<F> getFilterClass();

    protected boolean shouldIgnore(Field field) {
        return List.of("orderBy", "orderDirection", "page", "pageSize").contains(field.getName());
    }

    protected Predicate buildPredicate(Root<E> root, CriteriaBuilder criteriaBuilder, Field field, Object value) {
        if (field.getType().equals(String.class)) {
            return criteriaBuilder.like(criteriaBuilder.lower(root.get(field.getName())), "%" + value.toString().toLowerCase() + "%");
        }

        return criteriaBuilder.equal(root.get(field.getName()), value);
    }
}