package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.inscricao.DTOs.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InscricaoController {

    private final InscricaoService inscricaoService;
    private final UsuarioAutenticado usuarioAutenticado;

    /**
     * Auto-inscrição. NÃO recebe identidade alguma no corpo — o membro vem do JWT.
     * É o que torna esta rota impossível de usar errado: não há campo a adulterar.
     */
    @PostMapping("/eventos/{eventoId}/inscricoes")
    public ResponseEntity<MinhaInscricaoResponse> inscrever(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        var response = inscricaoService.inscrever(
                eventoId, usuario.getPessoa().getId(), null, usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/eventos/{eventoId}/inscricoes/minha")
    public ResponseEntity<MinhaInscricaoResponse> minhaInscricao(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        return ResponseEntity.ok(
                inscricaoService.minhaInscricao(eventoId, usuario.getPessoa().getId()));
    }

    /** Inscrever outras pessoas: aqui os ids VÊM do cliente, então são validados um a um. */
    @PostMapping("/eventos/{eventoId}/inscricoes/pessoas")
    public ResponseEntity<Void> inscreverPessoas(@PathVariable UUID eventoId,
                                                 @Valid @RequestBody InscreverPessoasRequest data) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.inscreverPessoas(eventoId, data.pessoaIds(),
                usuario.getId(), usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/acompanhantes")
    public ResponseEntity<AcompanhanteResponse> adicionarAcompanhante(
            @PathVariable UUID eventoId,
            @PathVariable UUID inscricaoId,
            @Valid @RequestBody AcompanhanteRequest data) {
        var usuario = usuarioAutenticado.get();
        var response = inscricaoService.adicionarAcompanhante(
                inscricaoId, data, usuario.getId(), usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lista de inscritos — ADMIN e LÍDER só (travado no SecurityConfig). */
    @GetMapping("/eventos/{eventoId}/inscricoes")
    public ResponseEntity<ListaInscritosResponse> listar(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(inscricaoService.listarInscritos(
                eventoId, usuarioAutenticado.getIgrejaId()));
    }

    /**
     * Lista de participantes reduzida — QUALQUER MEMBRO autenticado (travado no
     * SecurityConfig por "/eventos/*&#47;inscricoes/**", que casa este path e NÃO o exato
     * "/eventos/*&#47;inscricoes" acima, restrito a ADMIN/LÍDER).
     */
    @GetMapping("/eventos/{eventoId}/inscricoes/participantes")
    public ResponseEntity<List<ParticipanteResponse>> listarParticipantes(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(inscricaoService.listarParticipantes(
                eventoId, usuarioAutenticado.getIgrejaId()));
    }

    @DeleteMapping("/inscricoes/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.cancelar(id, usuario.getId(), usuario.getPessoa().getId(),
                usuario.getRole().getNome(), usuario.getIgreja().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/acompanhantes/{id}")
    public ResponseEntity<Void> removerAcompanhante(@PathVariable UUID id) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.removerAcompanhante(id, usuario.getPessoa().getId(),
                usuario.getRole().getNome(), usuario.getIgreja().getId());
        return ResponseEntity.noContent().build();
    }
}
