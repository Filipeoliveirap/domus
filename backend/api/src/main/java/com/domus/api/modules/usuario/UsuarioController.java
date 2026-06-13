package com.domus.api.modules.usuario;

import com.domus.api.modules.usuario.DTO.UsuarioRequestDTO;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody UsuarioRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UsuarioResponseDTO response = usuarioService.registrarUsuario(data, igrejaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
