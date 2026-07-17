package com.domus.api.modules.membro;

import com.domus.api.modules.membro.DTO.MembroRequestDTO;
import com.domus.api.modules.membro.DTO.MembroResponse;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/membros")
@RequiredArgsConstructor
public class MembroController {

    private final MembroService membroService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<PagedResponse<MembroResponse>> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(membroService.listarMembros(igrejaId, q, pageable));
    }

    @PostMapping
    public ResponseEntity<MembroResponse> cadastrar(
            @Valid @RequestBody MembroRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        MembroResponse response = membroService.cadastrarMembro(data, igrejaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MembroResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody MembroRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(membroService.atualizarMembro(id, data, igrejaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MembroResponse> buscarPorId(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(membroService.buscarPorId(id, igrejaId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        membroService.arquivarMembro(id, igrejaId);
        return ResponseEntity.noContent().build();
    }
}