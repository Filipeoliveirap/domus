package com.domus.api.modules.notificacao;

import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;
    private final NotificacaoSseRegistry sseRegistry;
    private final UsuarioAutenticado usuarioAutenticado;

    /** Empurra um sinal ("aconteceu algo novo") assim que uma notificação é criada — o
     *  cliente reage buscando a lista/contagem de verdade pelos mesmos endpoints de sempre,
     *  não confia no corpo do evento SSE. X-Accel-Buffering evita que um proxy reverso
     *  na frente (nginx, etc.) segure a resposta em buffer esperando ela "terminar".
     *  IMPORTANTE: retornar SseEmitter direto, nunca embrulhado em ResponseEntity<SseEmitter>
     *  — o HttpEntityMethodProcessor não inicia o dispatch assíncrono corretamente nesse caso
     *  e a resposta trava sem nunca comitar os headers (achado depurando em 2026-08-21). */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return sseRegistry.registrar(usuarioId);
    }

    @GetMapping
    public PagedResponse<com.domus.api.modules.notificacao.DTO.NotificacaoResponse> listar(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        Pageable ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificacaoService.listar(usuarioId, ordenado);
    }

    @GetMapping("/contagem-nao-lidas")
    public Map<String, Long> contagemNaoLidas() {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return Map.of("total", notificacaoService.contarNaoLidas(usuarioId));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(@PathVariable UUID id) {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        notificacaoService.marcarComoLida(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarTodasComoLidas() {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        notificacaoService.marcarTodasComoLidas(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
