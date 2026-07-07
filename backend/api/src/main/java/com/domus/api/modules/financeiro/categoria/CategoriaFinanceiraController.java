package com.domus.api.modules.financeiro.categoria;

import com.domus.api.modules.financeiro.categoria.DTOs.*;
import com.domus.api.shared.PagedResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaFinanceiraController {

    private final CategoriaFinanceiraService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public PagedResponse<CategoriaResponse> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.listar(igrejaId, termo, pageable);
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscar(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.buscarPorId(id, igrejaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse cadastrar(@Valid @RequestBody CategoriaRequestDTO dto) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.cadastrar(dto, igrejaId);
    }

    @PutMapping("/{id}")
    public CategoriaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequestDTO dto) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.atualizar(id, dto, igrejaId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        service.arquivar(id, igrejaId);
    }

    @GetMapping("/todas")
    public List<CategoriaResponse> listarTodas() {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.listarTodas(igrejaId);
    }
}