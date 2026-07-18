package com.domus.api.modules.inicio;

import com.domus.api.modules.inicio.dto.InicioResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inicio")
@RequiredArgsConstructor
public class InicioController {

    private final InicioService inicioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public InicioResponse inicio() {
        return inicioService.carregar(usuarioAutenticado.getIgrejaId());
    }
}
