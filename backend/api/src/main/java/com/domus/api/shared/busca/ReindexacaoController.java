package com.domus.api.shared.busca;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/reindexacao")
@RequiredArgsConstructor
public class ReindexacaoController {

    private final ReindexacaoService reindexacaoService;
    @PostMapping
    public Map<String, Long> reindexar() {
        return reindexacaoService.reindexarTudo();
    }
}