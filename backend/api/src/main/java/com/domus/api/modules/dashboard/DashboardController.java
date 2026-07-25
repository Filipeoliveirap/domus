package com.domus.api.modules.dashboard;

import com.domus.api.modules.dashboard.dto.DashboardResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UsuarioAutenticado usuarioAutenticado;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
    }

    @GetMapping
    public DashboardResponse dashboard() {
        exigirFinanceiro();
        return dashboardService.carregar(usuarioAutenticado.getIgrejaId());
    }
}
