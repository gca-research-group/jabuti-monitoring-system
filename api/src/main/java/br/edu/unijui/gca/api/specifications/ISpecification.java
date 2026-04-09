package br.edu.unijui.gca.api.specifications;

import org.springframework.data.jpa.domain.Specification;


public interface ISpecification<E, F> {
    Specification<E> build(F filter);
}

