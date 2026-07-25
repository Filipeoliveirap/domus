package com.domus.api.modules.usuario;

import com.domus.api.modules.pessoa.DTO.ConcederAcessoRequestDTO;
import com.domus.api.modules.usuario.DTO.*;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @PostMapping("/conceder-acesso")
    public ResponseEntity<UsuarioResponseDTO> concederAcesso(@Valid @RequestBody ConcederAcessoRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.concederAcesso(data, igrejaId));
    }

    @PostMapping("/reativar-acesso")
    public ResponseEntity<UsuarioResponseDTO> reativarAcesso(@Valid @RequestBody ConcederAcessoRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UsuarioResponseDTO response = usuarioService.reativarAcesso(data, igrejaId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reenviar-convite")
    public ResponseEntity<Void> reenviarConvite(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        usuarioService.reenviarConvite(id, igrejaId);
        return ResponseEntity.noContent().build();
    }


    @GetMapping
    public PagedResponse<UsuarioResponseDTO> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {  // sem sort no default
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        Pageable semSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return usuarioService.listar(igrejaId, termo, semSort);
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
    public ResponseEntity<Void> arquivarUsuario(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        usuarioService.arquivarUsuario(id, igrejaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/capacidades")
    public ResponseEntity<Void> concederCapacidade(@PathVariable UUID id,
                                                    @Valid @RequestBody CapacidadeRequest data) {
        usuarioService.concederCapacidade(id, data.capacidade(),
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/capacidades/{capacidade}")
    public ResponseEntity<Void> revogarCapacidade(@PathVariable UUID id,
                                                   @PathVariable String capacidade) {
        usuarioService.revogarCapacidade(id, capacidade, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }
}
