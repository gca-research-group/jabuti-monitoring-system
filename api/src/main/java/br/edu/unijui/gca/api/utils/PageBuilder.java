package br.edu.unijui.gca.api.utils;

import br.edu.unijui.gca.api.dtos.BaseFilterDto;
import br.edu.unijui.gca.api.enums.OrderDirection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

public class PageBuilder {
    public static Pageable from(BaseFilterDto filterDto) {
        int page = filterDto.getPage() > 0 ? filterDto.getPage() - 1 : filterDto.getPage();
        Sort sort = StringUtils.hasText(filterDto.getOrderBy()) ? Sort.by(filterDto.getOrderBy()) : Sort.by("id");

        OrderDirection orderDirection = OrderDirection.fromValue(filterDto.getOrderDirection());

        if (OrderDirection.ASC.equals(orderDirection)) {
            sort.ascending();
        } else {
            sort.descending();
        }

        return PageRequest.of(
                page,
                filterDto.getPageSize(),
                sort
        );
    }
}
