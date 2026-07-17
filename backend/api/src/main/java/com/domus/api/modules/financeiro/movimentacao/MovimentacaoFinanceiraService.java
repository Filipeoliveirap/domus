package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraService;
import com.domus.api.modules.financeiro.movimentacao.DTOs.*;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MovimentacaoFinanceiraService {

    private final MovimentacaoFinanceiraRepository repository;
    private final CategoriaFinanceiraService categoriaService;
    private final IgrejaRepository igrejaRepository;
    private final MembroRepository membroRepository;
    private final UsuarioRepository usuarioRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;

    @Transactional(readOnly = true)
    public PagedResponse<MovimentacaoResponse> listar(UUID igrejaId, TipoMovimentacao tipo, UUID categoriaId,
                                                      LocalDate dataInicio, LocalDate dataFim, String q,
                                                      Pageable pageable) {
        boolean semFiltro = tipo == null && categoriaId == null
                && dataInicio == null && dataFim == null
                && (q == null || q.isBlank());

        if (semFiltro) {
            return listarSemFiltro(igrejaId, pageable);
        }
        String termo = (q == null || q.isBlank()) ? null : q.trim();

        LocalDate inicio = dataInicio != null ? dataInicio : LocalDate.of(1900, 1, 1);
        LocalDate fim = dataFim != null ? dataFim : LocalDate.of(2999, 12, 31);
        Page<MovimentacaoResponse> page = repository
                .buscarComFiltros(igrejaId, tipo, categoriaId, inicio, fim, termo, pageable)
                .map(MovimentacaoResponse::de);
        return PagedResponse.from(page);
    }

    @Cacheable(
            value = "movimentacoes",
            key = "T(com.domus.api.config.redis.CacheKeys).movimentacoes(#igrejaId, #pageable)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<MovimentacaoResponse> listarSemFiltro(UUID igrejaId, Pageable pageable) {
        Page<MovimentacaoResponse> page = repository
                .buscarComFiltros(igrejaId, null, null,
                        LocalDate.of(1900, 1, 1),
                        LocalDate.of(2999, 12, 31),
                        null, pageable)
                .map(MovimentacaoResponse::de);
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public MovimentacaoResponse buscarPorId(UUID id, UUID igrejaId) {
        MovimentacaoFinanceira m = repository.buscarPorIdComRelacoes(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada."));
        return MovimentacaoResponse.de(m);
    }

    @Transactional
    public MovimentacaoResponse cadastrar(MovimentacaoRequestDTO dto, UUID igrejaId, UUID usuarioId) {
        log.info("Cadastrando movimentação. tipo={}, valor={}, categoria_id={}, membro_id={}, criado_por={}, igreja_id={}",
                dto.tipo(), dto.valor(), dto.categoriaId(), dto.membroId(), usuarioId, igrejaId);

        CategoriaFinanceira categoria = categoriaService.buscarEntidade(dto.categoriaId(), igrejaId);
        validarCompatibilidade(dto.tipo(), categoria);

        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .categoria(categoria)
                .criadoPor(usuarioRepository.getReferenceById(usuarioId))
                .membro(resolverMembro(dto.membroId(), igrejaId))
                .tipo(dto.tipo())
                .valor(dto.valor())
                .dataMovimentacao(dto.dataMovimentacao())
                .descricao(dto.descricao())
                .build();

        repository.save(mov);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.CRIADO,
                mov.getId(),
                igrejaId
        );
        log.info("Movimentação cadastrada. id={}, tipo={}, valor={}, criado_por={}, igreja_id={}",
                mov.getId(), dto.tipo(), dto.valor(), usuarioId, igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);

        return buscarPorId(mov.getId(), igrejaId);
    }

    @Transactional
    public MovimentacaoResponse atualizar(UUID id, MovimentacaoRequestDTO dto, UUID igrejaId, UUID usuarioId) {
        log.info("Atualizando movimentação. id={}, novo_tipo={}, novo_valor={}, nova_categoria={}, atualizado_por={}, igreja_id={}",
                id, dto.tipo(), dto.valor(), dto.categoriaId(), usuarioId, igrejaId);

        MovimentacaoFinanceira mov = repository.buscarPorIdComRelacoes(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada."));

        CategoriaFinanceira categoria = categoriaService.buscarEntidade(dto.categoriaId(), igrejaId);
        validarCompatibilidade(dto.tipo(), categoria);

        mov.setCategoria(categoria);
        mov.setMembro(resolverMembro(dto.membroId(), igrejaId));
        mov.setTipo(dto.tipo());
        mov.setValor(dto.valor());
        mov.setDataMovimentacao(dto.dataMovimentacao());
        mov.setDescricao(dto.descricao());
        mov.setAtualizadoPor(usuarioRepository.getReferenceById(usuarioId));   // ← quem editou

        repository.save(mov);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.ATUALIZADO,
                mov.getId(),
                igrejaId
        );
        log.info("Movimentação atualizada. id={}, valor={}, atualizado_por={}, igreja_id={}",
                id, dto.valor(), usuarioId, igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);
        return buscarPorId(id, igrejaId);
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        MovimentacaoFinanceira mov = repository.buscarPorIdComRelacoes(id, igrejaId)
                .orElseThrow(() -> {
                    log.warn("Tentativa de arquivar movimentação inexistente. id={}, igreja_id={}", id, igrejaId);
                    return new ResourceNotFoundException("Movimentação não encontrada.");
                });
        log.info("Arquivando movimentação. id={}, tipo={}, valor={}, igreja_id={}",
                id, mov.getTipo(), mov.getValor(), igrejaId);
        repository.delete(mov);
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.REMOVIDO,
                mov.getId(),
                igrejaId
        );
        log.info("Movimentação arquivada. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);
    }

    private void validarCompatibilidade(TipoMovimentacao tipo, CategoriaFinanceira categoria) {
        TipoCategoria tc = categoria.getTipo();
        if (tc == TipoCategoria.AMBOS) return;

        boolean compativel =
                (tipo == TipoMovimentacao.ENTRADA && tc == TipoCategoria.ENTRADA) ||
                        (tipo == TipoMovimentacao.SAIDA && tc == TipoCategoria.SAIDA);

        if (!compativel) {
            log.warn("Tipo incompatível com a categoria. tipo_mov={}, tipo_cat={}, categoria={}",
                    tipo, tc, categoria.getNome());
            throw new BusinessException("TIPO_INCOMPATIVEL",
                    "A categoria \"" + categoria.getNome() + "\" não aceita movimentações do tipo " +
                            (tipo == TipoMovimentacao.ENTRADA ? "entrada" : "saída") + ".");
        }
    }

    private Membro resolverMembro(UUID membroId, UUID igrejaId) {
        if (membroId == null) return null;
        return membroRepository.findByIdAndIgrejaId(membroId, igrejaId)
                .orElseThrow(() -> {
                    log.warn("Membro informado na movimentação não encontrado na igreja. membro_id={}, igreja_id={}", membroId, igrejaId);
                    return new ResourceNotFoundException("Membro não encontrado.");
                });
    }

    @Transactional
    public void reindexarPorCategoria(UUID categoriaId, UUID igrejaId) {
        List<UUID> ids = repository.buscarIdsPorCategoria(categoriaId, igrejaId);
        if (ids.isEmpty()) return;
        log.info("Reindexando {} movimentações por alteração na categoria. categoria_id={}, igreja_id={}",
                ids.size(), categoriaId, igrejaId);
        ids.forEach(id -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.ATUALIZADO,
                id,
                igrejaId
        ));
    }

    @Transactional
    public void reindexarPorMembro(UUID membroId, UUID igrejaId) {
        List<UUID> ids = repository.buscarIdsPorMembro(membroId, igrejaId);
        if (ids.isEmpty()) return;
        log.info("Reindexando {} movimentações por alteração no membro. membro_id={}, igreja_id={}",
                ids.size(), membroId, igrejaId);
        ids.forEach(id -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.ATUALIZADO,
                id,
                igrejaId
        ));
    }
}