package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.relatorio.DTOs.*;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService service;
    private final FamiliaIgrejaService familiaService;
    private final UsuarioAutenticado usuarioAutenticado;

    /** {@code igrejaId} ausente = minha igreja; presente, só passa se pertencer à minha família (senão é IDOR). */
    private UUID escopo(UUID igrejaId) {
        return familiaService.resolverEscopo(usuarioAutenticado.getIgrejaId(), igrejaId);
    }

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
    }

    @GetMapping("/resumo")
    public ResumoPeriodoResponse resumo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) UUID igrejaId,
            @RequestParam(required = false) Vinculo vinculo) {
        exigirFinanceiro();
        return service.resumoPorPeriodo(escopo(igrejaId), dataInicio, dataFim, vinculo);
    }

    @GetMapping("/por-categoria")
    public List<CategoriaBreakdownResponse> porCategoria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) UUID igrejaId,
            @RequestParam(required = false) Vinculo vinculo) {
        exigirFinanceiro();
        return service.porCategoria(escopo(igrejaId), dataInicio, dataFim, vinculo);
    }

    @GetMapping("/evolucao-mensal")
    public List<EvolucaoMensalResponse> evolucaoMensal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) UUID igrejaId,
            @RequestParam(required = false) Vinculo vinculo) {
        exigirFinanceiro();
        return service.evolucaoMensal(escopo(igrejaId), dataInicio, dataFim, vinculo);
    }

    @GetMapping("/maior-lancamento")
    public MaiorLancamentoResponse maiorLancamento(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) UUID igrejaId,
            @RequestParam(required = false) Vinculo vinculo) {
        exigirFinanceiro();
        return service.maiorLancamento(escopo(igrejaId), dataInicio, dataFim, vinculo);
    }

    @GetMapping("/por-contribuinte")
    public List<ContribuinteBreakdownResponse> porContribuinte(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) UUID igrejaId,
            @RequestParam(required = false) Vinculo vinculo) {
        exigirFinanceiro();
        return service.porContribuinte(escopo(igrejaId), dataInicio, dataFim, vinculo);
    }
}