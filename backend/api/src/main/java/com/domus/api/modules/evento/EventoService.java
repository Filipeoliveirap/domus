package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;
    private final IgrejaRepository igrejaRepository;
    private final StringRedisTemplate redisTemplate;

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
                .orElseThrow(() -> new BusinessException("Evento não encontrado."));
        return EventoResponse.from(evento);
    }

    @Transactional
    public EventoResponse cadastrarEvento(EventoRequest data, UUID igrejaId) {
        validarDatas(data);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new BusinessException("Igreja não encontrada."));

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
        evictCacheEventos(igrejaId);
        return EventoResponse.from(salvo);
    }

    @Transactional
    public EventoResponse atualizarEvento(UUID id, EventoRequest data, UUID igrejaId) {
        validarDatas(data);

        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Evento não encontrado."));

        evento.setTitulo(data.titulo());
        evento.setDescricao(data.descricao());
        evento.setInicioEm(data.inicioEm());
        evento.setFimEm(data.fimEm());
        evento.setLocal(data.local());
        evento.setFoto(data.foto());

        Evento salvo = eventoRepository.save(evento);
        evictCacheEventos(igrejaId);
        return EventoResponse.from(salvo);
    }

    @Transactional
    public void arquivarEvento(UUID id, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new BusinessException("Evento não encontrado."));
        eventoRepository.delete(evento);
        evictCacheEventos(igrejaId);
    }

    private void validarDatas(EventoRequest data) {
        if (data.fimEm() != null && data.fimEm().isBefore(data.inicioEm())) {
            throw new BusinessException("DATA_INVALIDA",
                    "A data de término não pode ser anterior à data de início.");
        }
    }

    private void evictCacheEventos(UUID igrejaId) {
        try {
            String pattern = "eventos::" + igrejaId + ":*";
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                List<String> keys = new ArrayList<>();
                cursor.forEachRemaining(keys::add);
                if (!keys.isEmpty()) redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao invalidar cache de eventos. igreja_id={}", igrejaId, ex);
        }
    }
}