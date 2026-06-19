package br.edu.unijui.gca.api.mappers;

import br.edu.unijui.gca.api.dtos.FindAllResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

import java.util.List;

public class FindAllResponseMapper {
    public static <T> FindAllResponseDto<List<T>> fromPage(Page<T> page) {
        return FindAllResponseDto.<List<T>>builder()
                .data(page.getContent())
                .total(page.getTotalElements())
                .pages(page.getTotalPages())
                .page(page.getNumber() + 1)
                .hasMore(page.hasNext())
                .build();
    }

    public static <T> FindAllResponseDto<List<T>> fromSlice(Slice<T> slice) {
        return FindAllResponseDto.<List<T>>builder()
                .data(slice.getContent())
                .hasMore(slice.hasNext())
                .build();
    }
}
