package com.domus.api.modules.usuario;

import com.domus.api.modules.usuario.DTO.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @PostMapping("/registrar")
    public ResponseEntity<UsuarioResponseDTO> criar(@Valid @RequestBody UsuarioRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UsuarioResponseDTO response = usuarioService.registrarUsuario(data, igrejaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public PagedResponse<UsuarioResponseDTO> listar(@RequestParam(required = false) String q,
                                                    @PageableDefault(size = 20, sort = "nome") Pageable pageable) {

        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        return usuarioService.listar(igrejaId, termo, pageable);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioUpdateRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UsuarioResponseDTO response = usuarioService.usuarioUpdate(id, data, igrejaId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UsuarioResponseDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UsuarioResponseDTO response = usuarioService.updateStatus(id, data.ativo(),  igrejaId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UsuarioResponseDTO> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UsuarioResponseDTO response = usuarioService.updateRole(id, data.role(), igrejaId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> buscarPorId(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(usuarioService.buscarPorId(id, igrejaId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        usuarioService.deletarUsuario(id, igrejaId);
        return ResponseEntity.noContent().build();
    }
 }
