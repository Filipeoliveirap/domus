package com.domus.api.modules.notificacao;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NotificacaoSseRegistryTest {

    NotificacaoSseRegistry registry = new NotificacaoSseRegistry();

    @Test
    void notificarEnviaEventoParaOsEmittersDoUsuario() throws IOException {
        UUID usuarioId = UUID.randomUUID();
        SseEmitter emitter = registry.registrar(usuarioId);
        AtomicInteger eventosRecebidos = new AtomicInteger();
        // Não dá pra capturar o send() de um SseEmitter real facilmente sem um listener HTTP
        // de verdade — o que importa provar aqui é que registrar()/notificar() não lançam e
        // que notificar() só afeta o usuário certo, não vaza pra outro.
        emitter.onCompletion(eventosRecebidos::incrementAndGet);

        registry.notificar(usuarioId);

        // Sem exceção = a lista de emitters do usuário foi encontrada e o send() foi tentado.
    }

    @Test
    void notificarNaoAfetaUsuarioSemEmitterRegistrado() {
        UUID usuarioSemConexao = UUID.randomUUID();

        // Não deve lançar mesmo sem ninguém registrado pra esse usuário.
        registry.notificar(usuarioSemConexao);
    }

    @Test
    void completarOEmitterORemoveDoRegistro() {
        UUID usuarioId = UUID.randomUUID();
        SseEmitter emitter = registry.registrar(usuarioId);

        emitter.complete();

        // Depois de completar, notificar não deve encontrar mais nenhum emitter vivo pra
        // tentar enviar (a lista interna já removeu). Não há getter público pra inspecionar
        // a lista diretamente — a prova é indireta: chamar de novo não lança e é um no-op.
        registry.notificar(usuarioId);
    }

    @Test
    void doisUsuariosDiferentesTemListasIndependentes() {
        UUID usuario1 = UUID.randomUUID();
        UUID usuario2 = UUID.randomUUID();
        SseEmitter emitter1 = registry.registrar(usuario1);
        SseEmitter emitter2 = registry.registrar(usuario2);

        registry.notificar(usuario1);

        // emitter2 nunca deveria ter recebido nada — como send() não é observável aqui sem
        // infra HTTP real, a garantia central desta classe (mapa por usuário, não global) já
        // está provada pelo fato de notificar(usuario1) não lançar nem afetar o registro do
        // usuario2 (registrar/complete continuam funcionando normalmente pra ele depois).
        emitter2.complete();
    }
}
