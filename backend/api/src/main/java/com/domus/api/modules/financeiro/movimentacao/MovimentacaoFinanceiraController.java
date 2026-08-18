package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.modules.financeiro.movimentacao.DTOs.*;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoFinanceiraController {

    private final MovimentacaoFinanceiraService service;
    private final UsuarioAutenticado usuarioAutenticado;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
    }

    @GetMapping
    public PagedResponse<MovimentacaoResponse> listar(
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID pessoaId,
            @PageableDefault(size = 15) Pageable pageable) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.listar(igrejaId, tipo, categoriaId, dataInicio, dataFim, q, pessoaId, pageable);
    }

    @GetMapping("/totais")
    public MovimentacaoTotaisResponse totais(
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID pessoaId) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.totais(igrejaId, tipo, categoriaId, dataInicio, dataFim, q, pessoaId);
    }

    @GetMapping("/{id}")
    public MovimentacaoResponse buscar(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.buscarPorId(id, igrejaId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResponse cadastrar(@Valid @RequestBody MovimentacaoRequestDTO dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.cadastrar(dto, igrejaId, usuarioId);
    }

    @PutMapping("/{id}")
    public MovimentacaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody MovimentacaoRequestDTO dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.atualizar(id, dto, igrejaId, usuarioId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void arquivar(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        service.arquivar(id, igrejaId);
    }

    @GetMapping("/arquivadas")
    public java.util.List<MovimentacaoArquivadaResponse> arquivadas() {
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
}