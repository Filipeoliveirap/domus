package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.elegibilidade.DTOs.ElegibilidadeResponse;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/eventos")
@RequiredArgsConstructor
public class EventoController {

    private final EventoService eventoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<PagedResponse<EventoResponse>> listar(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 12) Pageable pageable) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        Pageable semSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(eventoService.listarEventos(igrejaId, termo, semSort));
    }

    /**
     * Sugestões de tipo para o autocomplete do cadastro. Precisa vir ANTES de
     * {@code GET /eventos/**} no SecurityConfig (matcher específico antes do curinga).
     */
    @GetMapping("/tipos")
    public ResponseEntity<List<String>> tipos() {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(eventoService.tiposSugeridos(igrejaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventoResponse> buscarPorId(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(eventoService.buscarPorId(id, igrejaId));
    }

    /**
     * Prévia da elegibilidade da PRÓPRIA PESSOA logada — conveniência de UX para a tela
     * decidir o que mostrar antes do POST de inscrição. NUNCA é defesa: ver Javadoc de
     * {@link EventoService#elegibilidade}.
     */
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
            @Valid @RequestBody EventoRequest data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return ResponseEntity.ok(eventoService.atualizarEvento(id, data, igrejaId, usuarioId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        eventoService.arquivarEvento(id, igrejaId);
        return ResponseEntity.noContent().build();
    }
}