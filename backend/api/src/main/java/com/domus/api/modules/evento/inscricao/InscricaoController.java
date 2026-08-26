package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.inscricao.DTOs.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
public class InscricaoController {

    private final InscricaoService inscricaoService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoService campoPersonalizadoService;

    @PostMapping("/eventos/{eventoId}/inscricoes")
    public ResponseEntity<MinhaInscricaoResponse> inscrever(
            @PathVariable UUID eventoId,
            @RequestParam(defaultValue = "false") boolean confirmado) {
        var usuario = usuarioAutenticado.get();
        var response = inscricaoService.inscrever(
                eventoId, usuario.getPessoa().getId(), null, usuario.getPessoa().getId(),
                usuario.getRole().getNome(), confirmado, usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/eventos/{eventoId}/inscricoes/minha")
    public ResponseEntity<MinhaInscricaoResponse> minhaInscricao(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        return ResponseEntity.ok(
                inscricaoService.minhaInscricao(eventoId, usuario.getPessoa().getId()));
    }

    @PostMapping("/eventos/{eventoId}/inscricoes/pessoas")
    public ResponseEntity<List<PessoaInscritaComCobranca>> inscreverPessoas(
            @PathVariable UUID eventoId,
            @RequestParam(defaultValue = "false") boolean confirmado,
            @Valid @RequestBody InscreverPessoasRequest data) {
        var usuario = usuarioAutenticado.get();
        java.util.Set<UUID> pessoasParaLink = data.pessoasParaLink() == null
                ? java.util.Set.of() : java.util.Set.copyOf(data.pessoasParaLink());
        var resultado = inscricaoService.inscreverPessoas(eventoId, data.pessoaIds(), pessoasParaLink,
                usuario.getId(), usuario.getPessoa().getId(), usuario.getRole().getNome(), confirmado,
                usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    @PostMapping("/eventos/{eventoId}/inscricoes/convidados")
    public ResponseEntity<ConvidadoResponse> criarConvidado(
            @PathVariable UUID eventoId,
            @Valid @RequestBody CriarConvidadoRequest data) {
        var usuario = usuarioAutenticado.get();
        var resultado = inscricaoService.inscreverConvidado(
                eventoId, usuario.getIgreja().getId(), data.nome(), data.telefone(), data.email(),
                usuario.getPessoa().getId(), usuario.getId(), data.visitanteId(), data.gerarLink());
        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responder(resultado.inscricao().getId(), null, data.respostas(),
                    usuario.getIgreja().getId(), usuario.getPessoa().getId(), usuario.getRole().getNome());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConvidadoResponse.from(resultado.inscricao(), resultado.cobranca()));
    }

    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/acompanhantes")
    public ResponseEntity<AcompanhanteResponse> adicionarAcompanhante(
            @PathVariable UUID eventoId,
            @PathVariable UUID inscricaoId,
            @Valid @RequestBody AcompanhanteRequest data) {
        var usuario = usuarioAutenticado.get();
        var response = inscricaoService.adicionarAcompanhante(
                inscricaoId, data, usuario.getId(), usuario.getPessoa().getId(),
                usuario.getRole().getNome(), usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lista de inscritos — ADMIN e LÍDER só (travado no SecurityConfig). */
    @GetMapping("/eventos/{eventoId}/inscricoes")
    public ResponseEntity<ListaInscritosResponse> listar(
            @PathVariable UUID eventoId,
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String busca,
            @PageableDefault(size = 20) Pageable pageable) {
        String buscaFiltro = (busca == null || busca.isBlank()) ? null : busca.trim();
        Pageable semSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(inscricaoService.listarInscritos(
                eventoId, usuarioAutenticado.getIgrejaId(), buscaFiltro, semSort));
    }

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

    @GetMapping("/inscricoes/{inscricaoId}/respostas")
    public ResponseEntity<List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaResponse>> respostas(
            @PathVariable UUID inscricaoId,
            @RequestParam(required = false) UUID acompanhanteId) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(campoPersonalizadoService.respostasPorInscricao(inscricaoId, acompanhanteId, igrejaId));
    }

    @PutMapping("/inscricoes/{inscricaoId}/respostas")
    public ResponseEntity<Void> responder(
            @PathVariable UUID inscricaoId,
            @RequestParam(required = false) UUID acompanhanteId,
            @jakarta.validation.Valid @RequestBody
            List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> dados) {
        campoPersonalizadoService.responder(inscricaoId, acompanhanteId, dados,
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), usuarioAutenticado.getRole());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/eventos/{eventoId}/presenca/marcar-todos")
    public ResponseEntity<Void> marcarTodosPresentes(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.marcarTodosPresentes(eventoId, usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/eventos/{eventoId}/presenca/desmarcar-todos")
    public ResponseEntity<Void> desmarcarTodosPresentes(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.desmarcarTodosPresentes(eventoId, usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/eventos/{eventoId}/presenca/inscricoes/{inscricaoId}")
    public ResponseEntity<Void> marcarPresencaInscricao(
            @PathVariable UUID eventoId,
            @PathVariable UUID inscricaoId,
            @Valid @RequestBody MarcarPresencaRequest data) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.marcarPresencaInscricao(eventoId, inscricaoId, data.compareceu(),
                usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/eventos/{eventoId}/presenca/acompanhantes/{acompanhanteId}")
    public ResponseEntity<Void> marcarPresencaAcompanhante(
            @PathVariable UUID eventoId,
            @PathVariable UUID acompanhanteId,
            @Valid @RequestBody MarcarPresencaRequest data) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.marcarPresencaAcompanhante(eventoId, acompanhanteId, data.compareceu(),
                usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }
}
