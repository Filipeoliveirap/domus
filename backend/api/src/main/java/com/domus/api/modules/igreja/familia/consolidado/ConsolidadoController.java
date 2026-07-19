package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Fica sob {@code /relatorios} porque é onde o pastor já vai ver números — e porque o
 * {@code SecurityConfig} já trava {@code /relatorios/**} em ADMIN_IGREJA.
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

    @GetMapping
    public ConsolidadoResponse consolidado(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {
        return service.gerar(usuarioAutenticado.getIgrejaId(), dataInicio, dataFim);
    }
}
