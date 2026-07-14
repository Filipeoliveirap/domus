package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.relatorio.DTOs.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/resumo")
    public ResumoPeriodoResponse resumo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.resumoPorPeriodo(usuarioAutenticado.getIgrejaId(), dataInicio, dataFim);
    }

    @GetMapping("/por-categoria")
    public List<CategoriaBreakdownResponse> porCategoria(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.porCategoria(usuarioAutenticado.getIgrejaId(), dataInicio, dataFim);
    }

    @GetMapping("/evolucao-mensal")
    public List<EvolucaoMensalResponse> evolucaoMensal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.evolucaoMensal(usuarioAutenticado.getIgrejaId(), dataInicio, dataFim);
    }
<<<<<<< HEAD
=======

    @GetMapping("/maior-lancamento")
    public MaiorLancamentoResponse maiorLancamento(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.maiorLancamento(usuarioAutenticado.getIgrejaId(), dataInicio, dataFim);
    }
>>>>>>> develop
}