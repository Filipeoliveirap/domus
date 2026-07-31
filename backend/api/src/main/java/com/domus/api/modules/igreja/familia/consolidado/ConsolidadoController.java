package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Fica sob {@code /relatorios} porque é onde o pastor já vai ver números — mas o matcher
 * do {@code SecurityConfig} pra {@code /relatorios/**} libera ADMIN/LÍDER/COMUM (por causa
 * do {@code RelatorioController}), então a autorização financeira real é feita aqui dentro.
 *
 * <p>Não recebe {@code igrejaId}: o escopo é sempre a família de quem pergunta, calculada
 * no servidor a partir do JWT. Não há o que forjar.
 */
@RestController
@RequestMapping("/relatorios/congregacoes")
@RequiredArgsConstructor
public class ConsolidadoController {

    private final ConsolidadoService service;
    private final UsuarioAutenticado usuarioAutenticado;
    private final FamiliaIgrejaService familiaIgrejaService;

    @GetMapping
    public ConsolidadoResponse consolidado(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(), usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException("Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
        if (familiaIgrejaService.ehFilha(usuarioAutenticado.getIgrejaId())) {
            throw new AccessDeniedException("O consolidado da família só é visível pela igreja sede.");
        }
        return service.gerar(usuarioAutenticado.getIgrejaId(), dataInicio, dataFim);
    }
}
