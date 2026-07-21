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
        return ResponseEntity.ok(
                membroService.listarMembros(igrejaId, q, pageable, podeVerDadosSensiveis()));
    }

    @GetMapping("/bairros")
    public ResponseEntity<java.util.List<String>> bairros() {
        return ResponseEntity.ok(membroService.listarBairros(usuarioAutenticado.getIgrejaId()));
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
        return ResponseEntity.ok(membroService.buscarPorId(id, igrejaId, podeVerDadosSensiveis()));
    }

    /**
     * Endereço e observações (notas pastorais) só saem da API para ADMIN_IGREJA.
     *
     * <p>LÍDER e MEMBRO enxergam o membro para o que precisam — contato, ministério, status —
     * mas não onde a pessoa mora nem o que foi anotado sobre ela. Barrar isso na tela não
     * adiantaria: o JSON está a um DevTools de distância.
     */
    private boolean podeVerDadosSensiveis() {
        return "ADMIN_IGREJA".equals(usuarioAutenticado.getRole());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        membroService.arquivarMembro(id, igrejaId);
        return ResponseEntity.noContent().build();
    }
}