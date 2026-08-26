package com.domus.api.modules.financeiro.categoria;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.DTOs.*;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ConflitoNegocioException;
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
    private final MovimentacaoFinanceiraRepository movimentacaoFinanceiraRepository;
    private static final UUID SEM_IGNORAR = new UUID(0L, 0L);

    @Transactional(readOnly = true)
    @Cacheable(value = "categorias", key = "T(com.domus.api.config.redis.CacheKeys).categorias(#igrejaId, #q, #pageable)")
    public PagedResponse<CategoriaResponse> listar(UUID igrejaId, String q, Pageable pageable) {
        Page<CategoriaResponse> page = repository.buscarPorIgreja(igrejaId, q, pageable)
                .map(c -> CategoriaResponse.de(c, temMovimentacao(c.getId(), igrejaId)));
        return PagedResponse.from(page);
    }

    private boolean temMovimentacao(UUID categoriaId, UUID igrejaId) {
        return movimentacaoFinanceiraRepository.countByCategoriaIdAndIgrejaId(categoriaId, igrejaId) > 0;
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
    public List<CategoriaResponse> listarArquivadas(UUID igrejaId) {
        return repository.findArquivadasPorIgreja(igrejaId).stream()
                .map(c -> CategoriaResponse.de(c, temMovimentacao(c.getId(), igrejaId)))
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        int linhas = repository.restaurarPorId(id, igrejaId);
        if (linhas == 0) {
            throw new ResourceNotFoundException("Categoria não encontrada.");
        }
        outboxRegistrador.registrar(TipoEntidadeOutbox.CATEGORIA, TipoEventoOutbox.ATUALIZADO, id, igrejaId);
        cacheEvictor.evictPorIgreja("categorias", igrejaId);
    }

    @Transactional
    public void excluirDefinitivo(UUID id, UUID igrejaId) {
        // findByIdAndIgrejaIdIncluindoArquivadas (não buscarEntidade) porque esse endpoint
        // precisa achar também uma categoria já arquivada — é o caminho principal chamado
        // a partir da tela de Arquivadas.
        repository.findByIdAndIgrejaIdIncluindoArquivadas(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));

        // Diferente de Célula/Ministério: vínculo de categoria é histórico financeiro de
        // movimentações (possivelmente de outras pessoas) — apagar destruiria esse
        // histórico sem nenhum motivo de LGPD. Por isso BLOQUEIA, não desvincula.
        if (temMovimentacao(id, igrejaId)) {
            throw new ConflitoNegocioException("CATEGORIA_COM_MOVIMENTACAO",
                    "Não é possível apagar uma categoria que tem movimentações.");
        }
        // Rede de segurança (achado em teste, 2026-08-26): a guarda acima só vale no instante
        // em que checou — se uma movimentação for criada pra esta categoria entre o check e o
        // DELETE (corrida, sem lock nenhum aqui), o banco recusa por causa da FK. Sem isto, a
        // pessoa via um erro cru de banco em vez de "tem movimentação, não dá pra apagar".
        try {
            repository.hardDeleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflitoNegocioException("CATEGORIA_COM_MOVIMENTACAO",
                    "Não é possível apagar uma categoria que tem movimentações.");
        }
        outboxRegistrador.registrar(TipoEntidadeOutbox.CATEGORIA, TipoEventoOutbox.REMOVIDO, id, igrejaId);
        cacheEvictor.evictPorIgreja("categorias", igrejaId);
    }

    /**
     * Front decide se pede confirmação com base nisso; backend nunca recusa {@code atualizar}
     * por causa da contagem. IncluindoArquivadas: a tela de Arquivadas também usa este
     * endpoint pra explicar por que o excluir definitivo está bloqueado.
     */
    @Transactional(readOnly = true)
    public ContagemMovimentacoesResponse contarMovimentacoes(UUID id, UUID igrejaId) {
        repository.findByIdAndIgrejaIdIncluindoArquivadas(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        long total = movimentacaoFinanceiraRepository.countByCategoriaIdAndIgrejaId(id, igrejaId);
        return new ContagemMovimentacoesResponse(total);
    }

    /** Nomes de categoria por id, enxergando arquivadas. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> mapaNomesIncluindoArquivadas(List<UUID> ids, UUID igrejaId) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return repository.findByIdInAndIgrejaIdIncluindoArquivadas(ids, igrejaId).stream()
                .collect(java.util.stream.Collectors.toMap(CategoriaFinanceira::getId, CategoriaFinanceira::getNome));
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarTodas(UUID igrejaId) {
        return repository.buscarTodasPorIgreja(igrejaId)
                .stream()
                .map(CategoriaResponse::de)
                .toList();
    }
}