package com.domus.api.modules.financeiro.categoria;

import com.domus.api.modules.financeiro.categoria.DTOs.*;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
@Validated
public class CategoriaFinanceiraController {

    private final CategoriaFinanceiraService service;
    private final UsuarioAutenticado usuarioAutenticado;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
    }

    @GetMapping
    public PagedResponse<CategoriaResponse> listar(
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String q,
            @PageableDefault(size = 20) Pageable pageable) {
        exigirFinanceiro();
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.listar(igrejaId, termo, pageable);
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscar(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.buscarPorId(id, igrejaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse cadastrar(@Valid @RequestBody CategoriaRequestDTO dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.cadastrar(dto, igrejaId);
    }

    @PutMapping("/{id}")
    public CategoriaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequestDTO dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.atualizar(id, dto, igrejaId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        service.arquivar(id, igrejaId);
    }

    @GetMapping("/arquivadas")
    public List<CategoriaResponse> arquivadas() {
        exigirFinanceiro();
        return service.listarArquivadas(usuarioAutenticado.getIgrejaId());
    }

    @PostMapping("/{id}/restaurar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restaurar(@PathVariable UUID id) {
        exigirFinanceiro();
        service.restaurar(id, usuarioAutenticado.getIgrejaId());
    }

    @DeleteMapping("/{id}/definitivo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirDefinitivo(@PathVariable UUID id) {
        exigirFinanceiro();
        service.excluirDefinitivo(id, usuarioAutenticado.getIgrejaId());
    }

    /** A11: contagem de lançamentos da categoria, para o front decidir se pede confirmação
     *  antes de salvar a edição. Não bloqueia nada aqui — é só informação. */
    @GetMapping("/{id}/contagem-movimentacoes")
    public ContagemMovimentacoesResponse contarMovimentacoes(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.contarMovimentacoes(id, igrejaId);
    }

    @GetMapping("/todas")
    public List<CategoriaResponse> listarTodas() {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.listarTodas(igrejaId);
    }
}