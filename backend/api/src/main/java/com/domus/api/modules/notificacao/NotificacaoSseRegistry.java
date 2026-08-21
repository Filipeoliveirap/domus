package com.domus.api.modules.notificacao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registro em memória dos emitters SSE abertos por usuário — um usuário pode ter mais de um
 *  (várias abas/dispositivos), por isso é uma lista, não um único emitter. Em memória, sem
 *  Redis/pub-sub: só faz sentido complicar isso se o Domus rodar em mais de uma instância um
 *  dia (YAGNI — hoje é uma VPS só). Não carrega o conteúdo da notificação: manda só um sinal
 *  ("aconteceu algo novo"), o cliente reusa os mesmos hooks de sempre (React Query) pra buscar
 *  o dado de verdade — evita duplicar a serialização em dois lugares. */
@Component
@Slf4j
public class NotificacaoSseRegistry {

    private final Map<UUID, List<SseEmitter>> emittersPorUsuario = new ConcurrentHashMap<>();

    public SseEmitter registrar(UUID usuarioId) {
        // 0L = sem timeout — a conexão fica aberta até o cliente fechar ou a rede cair.
        SseEmitter emitter = new SseEmitter(0L);
        emittersPorUsuario.computeIfAbsent(usuarioId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remover = () -> {
            List<SseEmitter> emitters = emittersPorUsuario.get(usuarioId);
            if (emitters != null) emitters.remove(emitter);
        };
        emitter.onCompletion(remover);
        emitter.onTimeout(remover);
        emitter.onError(e -> remover.run());

        // Sem isto, o container só comita a resposta (envia os headers) no primeiro send()
        // de verdade — que pode nunca vir. O cliente (EventSource) fica preso em CONNECTING
        // indefinidamente, sem sequer abrir a conexão. Um comentário SSE (":", ignorado pelo
        // protocolo) força o flush imediato dos headers e confirma a conexão pro cliente.
        try {
            emitter.send(SseEmitter.event().comment("conectado"));
        } catch (IOException e) {
            remover.run();
        }

        return emitter;
    }

    public void notificar(UUID usuarioId) {
        List<SseEmitter> emitters = emittersPorUsuario.get(usuarioId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("nova-notificacao").data("novo"));
            } catch (IOException | IllegalStateException e) {
                // Conexão morta (aba fechada, rede caiu): remove e segue — não é erro do
                // produtor que gerou a notificação, ela já foi salva no banco.
                log.debug("Falha ao enviar SSE, removendo emitter. usuario_id={}", usuarioId, e);
                emitters.remove(emitter);
            }
        }
    }
}
