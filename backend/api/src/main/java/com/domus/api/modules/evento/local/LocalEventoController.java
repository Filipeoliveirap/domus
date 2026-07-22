package com.domus.api.modules.evento.local;

import com.domus.api.modules.evento.local.DTOs.LocalEventoRequest;
import com.domus.api.modules.evento.local.DTOs.LocalEventoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locais-evento")
@RequiredArgsConstructor
public class LocalEventoController {

    private final LocalEventoService localEventoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<LocalEventoResponse>> listar() {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(localEventoService.listar(igrejaId));
    }

    @PostMapping
    public ResponseEntity<LocalEventoResponse> criar(@Valid @RequestBody LocalEventoRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        LocalEventoResponse response = localEventoService.criar(data, igrejaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocalEventoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody LocalEventoRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(localEventoService.atualizar(id, data, igrejaId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        localEventoService.arquivar(id, igrejaId);
        return ResponseEntity.noContent().build();
    }
}
