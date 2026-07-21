package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final IgrejaRepository igrejaRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;
    private final InscricaoService inscricaoService;

    @Cacheable(
            value = "eventos",
            key = "T(com.domus.api.config.redis.CacheKeys).eventos(#igrejaId, #q, #pageable)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<EventoResponse> listarEventos(UUID igrejaId, String q, Pageable pageable) {
        Page<EventoResponse> pagina = eventoRepository.buscarPorIgreja(igrejaId, q, pageable)
                .map(EventoResponse::from);
        return PagedResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public EventoResponse buscarPorId(UUID id, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse cadastrarEvento(EventoRequest data, UUID igrejaId) {
        log.info("Cadastrando evento. titulo={}, igreja_id={}", data.titulo(), igrejaId);
        validarDatas(data);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        Evento evento = Evento.builder()
                .igreja(igreja)
                .titulo(com.domus.api.shared.util.TextoUtil.capitalizar(data.titulo()))
                .descricao(data.descricao())
                .inicioEm(data.inicioEm())
                .fimEm(data.fimEm())
                .local(com.domus.api.shared.util.TextoUtil.capitalizar(data.local()))
                .foto(data.foto())
                .vagas(data.vagas())
                .preco(data.preco())
                .exclusivoMembros(Boolean.TRUE.equals(data.exclusivoMembros()))
                .exclusivoBatizados(Boolean.TRUE.equals(data.exclusivoBatizados()))
                .requerInscricao(Boolean.TRUE.equals(data.requerInscricao()))
                .build();

        Evento salvo = eventoRepository.save(evento);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.CRIADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Evento cadastrado. id={}, igreja_id={}", salvo.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
        return EventoResponse.from(salvo);
    }

    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId) {
        log.info("Atualizando evento. id={}, igreja_id={}", id, igrejaId);
        validarDatas(data);

        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Editar um evento que já começou reescreveria o passado (data, local, vagas) debaixo
        // de gente que já confirmou presença ou já foi. AGENDADO é a única situação em que
        // editar ainda faz sentido.
        SituacaoEvento situacaoAtual = evento.getSituacao();
        if (situacaoAtual == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Não é possível editar um evento em andamento.");
        }
        if (situacaoAtual == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO",
                    "Não é possível editar um evento encerrado.");
        }

        evento.setTitulo(com.domus.api.shared.util.TextoUtil.capitalizar(data.titulo()));
        evento.setDescricao(data.descricao());
        evento.setInicioEm(data.inicioEm());
        evento.setFimEm(data.fimEm());
        evento.setLocal(com.domus.api.shared.util.TextoUtil.capitalizar(data.local()));
        evento.setFoto(data.foto());
        evento.setVagas(data.vagas());
        evento.setPreco(data.preco());
        boolean exclusivoMembros = Boolean.TRUE.equals(data.exclusivoMembros());
        boolean exclusivoBatizados = Boolean.TRUE.equals(data.exclusivoBatizados());
        evento.setExclusivoMembros(exclusivoMembros);
        evento.setExclusivoBatizados(exclusivoBatizados);
        evento.setRequerInscricao(Boolean.TRUE.equals(data.requerInscricao()));

        Evento salvo = eventoRepository.save(evento);

        // B4: restringir o evento pode deixar gente já confirmada inelegível — cancela quem
        // não se qualifica mais (mesmo cancelamento manual, convidados incluídos).
        int inscricoesRemovidas = inscricaoService.removerInscritosNaoElegiveis(
                salvo.getId(), exclusivoMembros, exclusivoBatizados);

        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Evento atualizado. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
        return EventoResponse.from(salvo, inscricoesRemovidas);
    }

    @Transactional
    public void arquivarEvento(UUID id, UUID igrejaId) {
        log.info("Arquivando evento. id={}, igreja_id={}", id, igrejaId);
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // Só bloqueia em andamento: arquivar um evento ENCERRADO é faxina normal (o piloto vai
        // acumular evento passado toda semana), e travar isso empurraria o usuário pra pedir
        // exceção sem necessidade. Em andamento é diferente — arquivar um evento que está
        // rolando AGORA tira ele da lista de quem ainda vai chegar.
        if (evento.getSituacao() == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Não é possível arquivar um evento em andamento.");
        }

        eventoRepository.delete(evento);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.REMOVIDO,
                evento.getId(),
                igrejaId
        );
        log.info("Evento arquivado. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
    }

    private void validarDatas(EventoRequest data) {
        if (data.fimEm() != null && data.fimEm().isBefore(data.inicioEm())) {
            log.warn("Data de término anterior ao início. inicio={}, fim={}", data.inicioEm(), data.fimEm());
            throw new BusinessException("DATA_INVALIDA",
                    "A data de término não pode ser anterior à data de início.");
        }
    }


}