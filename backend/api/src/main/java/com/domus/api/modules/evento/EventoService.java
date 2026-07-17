package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
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
                .titulo(data.titulo())
                .descricao(data.descricao())
                .inicioEm(data.inicioEm())
                .fimEm(data.fimEm())
                .local(data.local())
                .foto(data.foto())
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

        evento.setTitulo(data.titulo());
        evento.setDescricao(data.descricao());
        evento.setInicioEm(data.inicioEm());
        evento.setFimEm(data.fimEm());
        evento.setLocal(data.local());
        evento.setFoto(data.foto());

        Evento salvo = eventoRepository.save(evento);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.EVENTO,
                TipoEventoOutbox.ATUALIZADO,
                salvo.getId(),
                igrejaId
        );
        log.info("Evento atualizado. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("eventos", igrejaId);
        return EventoResponse.from(salvo);
    }

    @Transactional
    public void arquivarEvento(UUID id, UUID igrejaId) {
        log.info("Arquivando evento. id={}, igreja_id={}", id, igrejaId);
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
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