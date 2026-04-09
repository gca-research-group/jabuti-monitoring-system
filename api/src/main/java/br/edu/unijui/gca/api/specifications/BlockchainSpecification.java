package br.edu.unijui.gca.api.specifications;

import br.edu.unijui.gca.api.dtos.BlockchainFilterDto;
import br.edu.unijui.gca.api.entities.Blockchain;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BlockchainSpecification implements ISpecification<Blockchain, BlockchainFilterDto> {

    @Override
    public Specification<Blockchain> build(BlockchainFilterDto blockchainFilterDto) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (blockchainFilterDto.getName() != null) {
                predicates.add(criteriaBuilder.like(root.get("name"), blockchainFilterDto.getName()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

}
