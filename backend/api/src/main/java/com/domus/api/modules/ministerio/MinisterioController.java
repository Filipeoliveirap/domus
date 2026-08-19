package com.domus.api.modules.ministerio;

import com.domus.api.modules.ministerio.DTOs.*;
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
@RequestMapping("/ministerios")
@RequiredArgsConstructor
public class MinisterioController {

    private final MinisterioService ministerioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<MinisterioResponse>> listar() {
        return ResponseEntity.ok(ministerioService.listar(usuarioAutenticado.getIgrejaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinisterioDetalheResponse> detalhe(@PathVariable UUID id) {
        return ResponseEntity.ok(ministerioService.detalhe(
                id, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), souAdmin()));
    }

    @PostMapping
    public ResponseEntity<MinisterioResponse> criar(@Valid @RequestBody MinisterioRequest data) {
        exigirAdmin();
        MinisterioResponse response = ministerioService.criar(
                data, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinisterioResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody MinisterioRequest data) {
        exigirAdmin();
        return ResponseEntity.ok(ministerioService.atualizar(
                id, data, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        exigirAdmin();
        ministerioService.arquivar(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/arquivados")
    public ResponseEntity<List<MinisterioResponse>> arquivados() {
        exigirAdmin();
        return ResponseEntity.ok(ministerioService.listarArquivadas(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable UUID id) {
        exigirAdmin();
        ministerioService.restaurar(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/definitivo")
    public ResponseEntity<Void> excluirDefinitivo(@PathVariable UUID id) {
        exigirAdmin();
        ministerioService.excluirDefinitivo(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/membros")
    public ResponseEntity<Void> adicionarMembro(@PathVariable UUID id, @Valid @RequestBody AdicionarMembroRequest data) {
        ministerioService.adicionarMembro(id, data, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/membros/{pessoaId}")
    public ResponseEntity<Void> removerMembro(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        ministerioService.removerMembro(id, pessoaId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/membros/{pessoaId}/papel")
    public ResponseEntity<Void> atualizarPapel(@PathVariable UUID id, @PathVariable UUID pessoaId,
                                                @Valid @RequestBody AtualizarPapelRequest data) {
        exigirAdmin();
        ministerioService.atualizarPapel(id, pessoaId, data, usuarioAutenticado.getIgrejaId(), souAdmin());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pedidos")
    public ResponseEntity<Void> pedirEntrada(@PathVariable UUID id) {
        ministerioService.pedirEntrada(id, usuarioAutenticado.getPessoaId(), usuarioAutenticado.getIgrejaId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}/pedidos/{pessoaId}/aceitar")
    public ResponseEntity<Void> aceitarPedido(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        ministerioService.aceitarPedido(id, pessoaId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pedidos/{pessoaId}")
    public ResponseEntity<Void> recusarPedido(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        ministerioService.recusarPedido(id, pessoaId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin());
        return ResponseEntity.noContent().build();
    }

    private boolean souAdmin() {
        return Permissoes.podeGerenciarCadastroMinisterios(usuarioAutenticado.getRole());
    }

    private void exigirAdmin() {
        if (!Permissoes.podeGerenciarCadastroMinisterios(usuarioAutenticado.getRole())) {
            throw new AccessDeniedException("Só um administrador pode gerenciar o cadastro de ministérios.");
        }
    }
}
