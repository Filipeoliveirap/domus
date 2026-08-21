package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.elegibilidade.DTOs.ElegibilidadeResponse;
import com.domus.api.shared.DTO.PagedResponse;
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
@RequestMapping("/eventos")
@RequiredArgsConstructor
@Validated
public class EventoController {

    private final EventoService eventoService;
    private final EventoRelatorioService eventoRelatorioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<PagedResponse<EventoResponse>> listar(
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String q,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String recorteEtario,
            @PageableDefault(size = 12) Pageable pageable) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String role = usuarioAutenticado.getRole();
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        String tipoFiltro = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        String recorteFiltro = (recorteEtario == null || recorteEtario.isBlank()) ? null : recorteEtario.trim();
        Pageable semSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(eventoService.listarEventos(igrejaId, termo, tipoFiltro, recorteFiltro, role, semSort));
    }

    // Deve vir antes de GET /eventos/** no SecurityConfig (matcher específico antes do curinga).
    @GetMapping("/tipos")
    public ResponseEntity<List<String>> tipos() {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(eventoService.tiposSugeridos(igrejaId));
    }

    @GetMapping("/relatorio-geral")
    public ResponseEntity<com.domus.api.modules.evento.DTOs.RelatorioGeralResponse> relatorioGeral(
            @RequestParam(required = false) java.time.LocalDateTime inicio,
            @RequestParam(required = false) java.time.LocalDateTime fim,
            @RequestParam(required = false) String recorteEtario,
            @RequestParam(required = false) String tipo,
            @PageableDefault(size = 8) Pageable pageable) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String recorteFiltro = (recorteEtario == null || recorteEtario.isBlank()) ? null : recorteEtario.trim();
        String tipoFiltro = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        Pageable semSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(eventoRelatorioService.relatorioGeral(igrejaId, inicio, fim, recorteFiltro, tipoFiltro, semSort));
    }

    @GetMapping("/{id}/relatorio")
    public ResponseEntity<com.domus.api.modules.evento.DTOs.RelatorioEventoResponse> relatorio(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(eventoRelatorioService.relatorioIndividual(id, igrejaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> buscarPorId(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String role = usuarioAutenticado.getRole();
        return ResponseEntity.ok(eventoService.buscarPorId(id, igrejaId, role));
    }

    @GetMapping("/{id}/elegibilidade")
    public ResponseEntity<ElegibilidadeResponse> elegibilidade(@PathVariable UUID id) {
        var usuario = usuarioAutenticado.get();
        return ResponseEntity.ok(eventoService.elegibilidade(
                id, usuario.getPessoa().getId(), usuario.getIgreja().getId()));
    }

    @PostMapping
    public ResponseEntity<EventoResponse> cadastrar(@Valid @RequestBody EventoRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        EventoResponse response = eventoService.cadastrarEvento(data, igrejaId, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventoResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EventoRequest data,
            @RequestParam(defaultValue = "false") boolean cancelarNaoElegiveis,
            @RequestParam(defaultValue = "ESTA") com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return ResponseEntity.ok(eventoService.atualizarEvento(
                id, data, igrejaId, usuarioId, cancelarNaoElegiveis, escopo));
    }

    // Prévia de quem ficaria de fora se data fosse salvo, sem gravar. Restrito a quem gerencia eventos.
    @PostMapping("/{id}/impacto-restricao")
    public ResponseEntity<ImpactoRestricaoResponse> impactoRestricao(
            @PathVariable UUID id, @Valid @RequestBody EventoRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String role = usuarioAutenticado.getRole();
        return ResponseEntity.ok(eventoService.calcularImpacto(id, data, igrejaId, role));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ESTA") com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        eventoService.arquivarEvento(id, igrejaId, usuarioId, escopo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/arquivados")
    public ResponseEntity<List<com.domus.api.modules.evento.DTOs.EventoArquivadoResponse>> arquivados() {
        exigirGerenciar();
        return ResponseEntity.ok(eventoService.listarArquivados(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<Void> restaurar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ESTA") com.domus.api.modules.evento.serie.EscopoEdicaoEvento escopo) {
        exigirGerenciar();
        eventoService.restaurar(id, usuarioAutenticado.getIgrejaId(), escopo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/definitivo")
    public ResponseEntity<Void> excluirDefinitivo(@PathVariable UUID id) {
        exigirGerenciar();
        eventoService.excluirDefinitivo(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    private void exigirGerenciar() {
        if (!com.domus.api.shared.security.Permissoes.podeGerenciarEventos(usuarioAutenticado.getRole())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Só administradores e líderes gerenciam eventos.");
        }
    }
}