package com.domus.api.modules.termos;

import com.domus.api.shared.security.UsuarioAutenticado;
import com.domus.api.shared.web.ClienteIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/termos")
@RequiredArgsConstructor
public class TermoAceiteController {

    private final TermoAceiteService termoAceiteService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final ClienteIpResolver clienteIpResolver;

    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(HttpServletRequest request) {
        termoAceiteService.registrarAceite(usuarioAutenticado.getUsuarioId(), clienteIpResolver.resolver(request));
        return ResponseEntity.ok().build();
    }
}
