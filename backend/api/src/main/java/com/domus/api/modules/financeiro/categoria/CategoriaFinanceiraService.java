package com.domus.api.modules.financeiro.categoria;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.DTOs.*;
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoriaFinanceiraService {

    private final CategoriaFinanceiraRepository repository;
    private final IgrejaRepository  igrejaRepository;
    private final CacheEvictor cacheEvictor;
    private final ReindexacaoMovimentacaoService reindexacaoMovimentacaoService;
    private final OutboxRegistrador outboxRegistrador;
    private static final UUID SEM_IGNORAR = new UUID(0L, 0L);

    @Transactional(readOnly = true)
    @Cacheable(value = "categorias", key = "T(com.domus.api.config.redis.CacheKeys).categorias(#igrejaId, #q, #pageable)")
    public PagedResponse<CategoriaResponse> listar(UUID igrejaId, String q, Pageable pageable) {
        Page<CategoriaResponse> page = repository.buscarPorIgreja(igrejaId, q, pageable)
                .map(CategoriaResponse::de);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public CategoriaResponse buscarPorId(UUID id, UUID igrejaId) {
        return CategoriaResponse.de(buscarEntidade(id, igrejaId));
    }

    @Transactional(readOnly = true)
    public CategoriaFinanceira buscarEntidade(UUID id, UUID igrejaId) {
        return repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
    }

    @Transactional
    public CategoriaResponse cadastrar(CategoriaRequestDTO dto, UUID igrejaId) {
        log.info("Cadastrando categoria. nome={}, tipo={}, igreja_id={}", dto.nome(), dto.tipo(), igrejaId);
        String nome = com.domus.api.shared.util.TextoUtil.capitalizar(dto.nome());
        if (repository.existeComNome(igrejaId, nome, SEM_IGNORAR)) {
            log.warn("Categoria duplicada. nome={}, igreja_id={}", nome, igrejaId);
            throw new BusinessException("CATEGORIA_DUPLICADA", "Já existe uma categoria com esse nome.");
        }
        CategoriaFinanceira categoria = CategoriaFinanceira.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .nome(nome)
                .tipo(dto.tipo())
                .build();
        repository.save(categoria);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.CATEGORIA,
                TipoEventoOutbox.CRIADO,
                categoria.getId(),
                igrejaId
        );
        log.info("Categoria cadastrada. id={}, igreja_id={}", categoria.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("categorias", igrejaId);
        return CategoriaResponse.de(categoria);
    }

    @Transactional
    public CategoriaResponse atualizar(UUID id, CategoriaRequestDTO dto, UUID igrejaId) {
        log.info("Atualizando categoria. id={}, igreja_id={}", id, igrejaId);
        CategoriaFinanceira categoria = buscarEntidade(id, igrejaId);

        String nomeAntigo = categoria.getNome();
        String nome = com.domus.api.shared.util.TextoUtil.capitalizar(dto.nome());

        if (repository.existeComNome(igrejaId, nome, id)) {
            log.warn("Categoria duplicada na atualização. nome={}, igreja_id={}", nome, igrejaId);
            throw new BusinessException("CATEGORIA_DUPLICADA", "Já existe uma categoria com esse nome.");
        }
        categoria.setNome(nome);
        categoria.setTipo(dto.tipo());
        repository.save(categoria);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.CATEGORIA,
                TipoEventoOutbox.ATUALIZADO,
                categoria.getId(),
                igrejaId
        );
        log.info("Categoria atualizada. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("categorias", igrejaId);

        if (!nome.equals(nomeAntigo)) {
            reindexacaoMovimentacaoService.reindexarPorCategoria(id, igrejaId);
        }

        return CategoriaResponse.de(categoria);
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        log.info("Arquivando categoria. id={}, igreja_id={}", id, igrejaId);
        CategoriaFinanceira categoria = buscarEntidade(id, igrejaId);
        repository.delete(categoria);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.CATEGORIA,
                TipoEventoOutbox.REMOVIDO,
                categoria.getId(),
                igrejaId
        );
        log.info("Categoria arquivada. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("categorias", igrejaId);
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarTodas(UUID igrejaId) {
        return repository.buscarTodasPorIgreja(igrejaId)
                .stream()
                .map(CategoriaResponse::de)
                .toList();
    }
}