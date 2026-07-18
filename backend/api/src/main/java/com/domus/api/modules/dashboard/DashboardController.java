package com.domus.api.modules.dashboard;

import com.domus.api.modules.dashboard.dto.DashboardResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public DashboardResponse dashboard() {
        return dashboardService.carregar(usuarioAutenticado.getIgrejaId());
    }
}
