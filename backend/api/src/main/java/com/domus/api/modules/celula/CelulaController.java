package com.domus.api.modules.celula;

import com.domus.api.modules.celula.DTOs.*;
import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/celulas")
@RequiredArgsConstructor
public class CelulaController {

    private final CelulaService celulaService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<CelulaResponse>> listar() {
        return ResponseEntity.ok(celulaService.listar(
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CelulaDetalheResponse> detalhe(@PathVariable UUID id) {
        return ResponseEntity.ok(celulaService.detalhe(
                id, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId()));
    }

    @PostMapping
    public ResponseEntity<CelulaResponse> criar(@Valid @RequestBody CelulaRequest data) {
        exigirAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                celulaService.criar(data, usuarioAutenticado.getIgrejaId(),
                        usuarioAutenticado.getUsuarioId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CelulaResponse> atualizar(@PathVariable UUID id,
                                                     @Valid @RequestBody CelulaRequest data) {
        return ResponseEntity.ok(celulaService.atualizar(id, data,
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId(),
                usuarioAutenticado.getPessoaId(), souAdmin()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        exigirAdmin();
        celulaService.excluir(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/arquivados")
    public ResponseEntity<List<CelulaResponse>> arquivados() {
        exigirAdmin();
        return ResponseEntity.ok(celulaService.listarArquivadas(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable UUID id) {
        exigirAdmin();
        celulaService.restaurar(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/definitivo")
    public ResponseEntity<Void> excluirDefinitivo(@PathVariable UUID id) {
        exigirAdmin();
        celulaService.excluirDefinitivo(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/membros")
    public ResponseEntity<Void> adicionarMembro(@PathVariable UUID id,
                                                 @Valid @RequestBody AdicionarMembroCelulaRequest data) {
        celulaService.adicionarMembro(id, data, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/membros/{membroId}")
    public ResponseEntity<Void> removerMembro(@PathVariable UUID id,
                                               @PathVariable UUID membroId) {
        celulaService.removerMembro(id, membroId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/membros/{membroId}/papel")
    public ResponseEntity<Void> atualizarPapel(@PathVariable UUID id,
                                                @PathVariable UUID membroId,
                                                @Valid @RequestBody AtualizarPapelCelulaRequest data) {
        celulaService.atualizarPapel(id, membroId, data, usuarioAutenticado.getIgrejaId(), souAdmin(),
                usuarioAutenticado.getUsuarioId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/converter/{visitanteId}")
    public ResponseEntity<PessoaResponse> converterVisitante(@PathVariable UUID id,
                                                              @PathVariable UUID visitanteId,
                                                              @Valid @RequestBody ConverterVisitanteRequest data) {
        var pessoa = celulaService.converterVisitante(id, visitanteId, data,
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(),
                souAdmin(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.ok(PessoaResponse.from(pessoa));
    }

    private boolean souAdmin() {
        return Permissoes.podeGerenciarCelulas(usuarioAutenticado.getRole());
    }

    private void exigirAdmin() {
        if (!Permissoes.podeGerenciarCelulas(usuarioAutenticado.getRole())) {
            throw new AccessDeniedException(
                    "Só um administrador pode gerenciar o cadastro de células.");
        }
    }
}
