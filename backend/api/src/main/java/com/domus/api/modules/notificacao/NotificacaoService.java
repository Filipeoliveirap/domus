package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.DTO.NotificacaoResponse;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/** Fachada única de notificação in-app. Todo produtor (MinisterioService, CelulaService,
 *  UsuarioService, InscricaoService, EventoService, VinculoService, ExclusaoIgrejaJob) chama
 *  {@link #criar} no ponto onde o evento de negócio já acontece — síncrono, mesma transação
 *  do produtor, sem fila. Não conhece nem precisa conhecer célula/ministério/evento: quem
 *  monta o texto e resolve o(s) destinatário(s) é sempre o produtor. */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoSseRegistry sseRegistry;

    @Transactional
    public void criar(TipoNotificacao tipo, UUID igrejaId, UUID usuarioDestinatarioId, String texto, String link) {
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .usuarioDestinatario(usuarioRepository.getReferenceById(usuarioDestinatarioId))
                .tipo(tipo)
                .texto(texto)
                .link(link)
                .lida(false)
                .build());

        avisarPorSseDepoisDoCommit(usuarioDestinatarioId);
    }

    /** O aviso SSE só sai depois que a transação do produtor confirma de verdade — senão o
     *  cliente recarregaria pra buscar uma notificação que pode nem existir mais (a transação
     *  do produtor pode dar rollback depois deste ponto). Fora de uma transação (não deveria
     *  acontecer em produção, mas cobre testes chamando o serviço direto), avisa na hora. */
    private void avisarPorSseDepoisDoCommit(UUID usuarioDestinatarioId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseRegistry.notificar(usuarioDestinatarioId);
                }
            });
        } else {
            sseRegistry.notificar(usuarioDestinatarioId);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificacaoResponse> listar(UUID usuarioId, Pageable pageable) {
        Page<NotificacaoResponse> pagina = notificacaoRepository
                .findByUsuarioDestinatarioId(usuarioId, pageable)
                .map(NotificacaoResponse::from);
        return PagedResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID usuarioId) {
        return notificacaoRepository.countByUsuarioDestinatarioIdAndLidaFalse(usuarioId);
    }

    @Transactional
    public void marcarComoLida(UUID id, UUID usuarioId) {
        notificacaoRepository.findByIdAndUsuarioDestinatarioId(id, usuarioId).ifPresent(n -> {
            n.setLida(true);
            notificacaoRepository.save(n);
        });
    }

    @Transactional
    public void marcarTodasComoLidas(UUID usuarioId) {
        List<Notificacao> naoLidas = notificacaoRepository.findByUsuarioDestinatarioIdAndLidaFalse(usuarioId);
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
    }
}
