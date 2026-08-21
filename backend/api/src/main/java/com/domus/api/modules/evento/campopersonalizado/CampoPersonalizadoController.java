package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/eventos/{eventoId}/campos-personalizados")
@RequiredArgsConstructor
public class CampoPersonalizadoController {

    private final CampoPersonalizadoService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<CampoPersonalizadoResponse>> listar(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(service.listar(eventoId, usuarioAutenticado.getIgrejaId()));
    }

    @PutMapping
    public ResponseEntity<List<CampoPersonalizadoResponse>> salvar(
            @PathVariable UUID eventoId,
            @Valid @RequestBody List<CampoPersonalizadoRequest> dados) {
        return ResponseEntity.ok(service.salvar(eventoId, usuarioAutenticado.getIgrejaId(), dados));
    }
}
