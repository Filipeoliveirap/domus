package com.domus.api.modules.notificacao;

import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;
    private final UsuarioAutenticado usuarioAutenticado;

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
