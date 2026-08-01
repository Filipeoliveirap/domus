package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class BalanceteController {

    private final BalanceteService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/balancete-anual")
    public BalanceteResponseDTO balanceteAnual(@RequestParam int ano) {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(), usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException("Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
        return service.gerar(usuarioAutenticado.getIgrejaId(), ano);
    }
}
