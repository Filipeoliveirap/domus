package com.domus.api.modules.membro.DTO;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;


@Getter
@Setter
public class MembroPage<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public MembroPage() {}

    public MembroPage(List<T> content, int page, int size,
                         long totalElements, int totalPages, boolean last) {
        this.content = content; this.page = page; this.size = size;
        this.totalElements = totalElements; this.totalPages = totalPages; this.last = last;
    }

    public static <T> MembroPage<T> from(Page<T> p) {
        return new MembroPage<>(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages(), p.isLast());
    }

}
