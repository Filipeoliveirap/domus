package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraService;
import com.domus.api.modules.financeiro.movimentacao.DTOs.*;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MovimentacaoFinanceiraService {

    private final MovimentacaoFinanceiraRepository repository;
    private final CategoriaFinanceiraService categoriaService;
    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository membroRepository;
    private final UsuarioRepository usuarioRepository;
    private final CacheEvictor cacheEvictor;
    private final OutboxRegistrador outboxRegistrador;

    @Transactional(readOnly = true)
    public PagedResponse<MovimentacaoResponse> listar(UUID igrejaId, TipoMovimentacao tipo, UUID categoriaId,
                                                      LocalDate dataInicio, LocalDate dataFim, String q,
                                                      UUID pessoaId, Pageable pageable) {
        boolean semFiltro = tipo == null && categoriaId == null
                && dataInicio == null && dataFim == null
                && (q == null || q.isBlank()) && pessoaId == null;

        if (semFiltro) {
            return listarSemFiltro(igrejaId, pageable);
        }
        String termo = (q == null || q.isBlank()) ? null : q.trim();

        LocalDate inicio = dataInicio != null ? dataInicio : LocalDate.of(1900, 1, 1);
        LocalDate fim = dataFim != null ? dataFim : LocalDate.of(2999, 12, 31);
        Page<MovimentacaoFinanceira> pageEntidades = repository
                .buscarComFiltros(igrejaId, tipo, categoriaId, inicio, fim, termo, pessoaId, pageable);
        var nomesPorCategoria = nomesDeCategoria(pageEntidades.getContent(), igrejaId);
        var pessoasContribuintes = resolverPessoasContribuintes(pageEntidades.getContent());
        Page<MovimentacaoResponse> page = pageEntidades
                .map(m -> MovimentacaoResponse.de(m, nomesPorCategoria.get(m.getCategoria().getId()), pessoasContribuintes));
        return PagedResponse.from(page);
    }

    private java.util.Map<UUID, String> nomesDeCategoria(List<MovimentacaoFinanceira> movimentacoes, UUID igrejaId) {
        List<UUID> ids = movimentacoes.stream()
                .map(m -> m.getCategoria().getId())
                .distinct()
                .toList();
        return categoriaService.mapaNomesIncluindoArquivadas(ids, igrejaId);
    }

    /** Em lote, via bypass do @SQLRestriction — contribuinte cuja pessoa está arquivada (mas
     *  não excluída) continua mostrando os dados reais, não "Pessoa removida do sistema". */
    private java.util.Map<UUID, Pessoa> resolverPessoasContribuintes(List<MovimentacaoFinanceira> movimentacoes) {
        List<UUID> ids = movimentacoes.stream()
                .flatMap(m -> m.getContribuintes().stream())
                .map(c -> c.getPessoa())
                .filter(p -> p != null)
                .map(Pessoa::getId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return membroRepository.findByIdInIncluindoArquivadas(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Pessoa::getId, p -> p));
    }

    @Transactional(readOnly = true)
    public MovimentacaoTotaisResponse totais(UUID igrejaId, TipoMovimentacao tipo, UUID categoriaId,
                                             LocalDate dataInicio, LocalDate dataFim, String q, UUID pessoaId) {
        String termo = (q == null || q.isBlank()) ? null : q.trim();
        LocalDate inicio = dataInicio != null ? dataInicio : LocalDate.of(1900, 1, 1);
        LocalDate fim = dataFim != null ? dataFim : LocalDate.of(2999, 12, 31);
        var t = repository.agregarTotaisComFiltros(igrejaId, tipo, categoriaId, inicio, fim, termo, pessoaId);
        return new MovimentacaoTotaisResponse(t.getTotalEntradas(), t.getTotalSaidas(), t.getQuantidade());
    }

    @Cacheable(
            value = "movimentacoes",
            key = "T(com.domus.api.config.redis.CacheKeys).movimentacoes(#igrejaId, #pageable)"
    )
    @Transactional(readOnly = true)
    public PagedResponse<MovimentacaoResponse> listarSemFiltro(UUID igrejaId, Pageable pageable) {
        Page<MovimentacaoFinanceira> pageEntidades = repository
                .buscarComFiltros(igrejaId, null, null,
                        LocalDate.of(1900, 1, 1),
                        LocalDate.of(2999, 12, 31),
                        null, null, pageable);
        var nomesPorCategoria = nomesDeCategoria(pageEntidades.getContent(), igrejaId);
        var pessoasContribuintes = resolverPessoasContribuintes(pageEntidades.getContent());
        Page<MovimentacaoResponse> page = pageEntidades
                .map(m -> MovimentacaoResponse.de(m, nomesPorCategoria.get(m.getCategoria().getId()), pessoasContribuintes));
        return PagedResponse.from(page);
    }

    @Transactional(readOnly = true)
    public MovimentacaoResponse buscarPorId(UUID id, UUID igrejaId) {
        // IncluindoArquivadas: a tela de Arquivadas também abre o detalhe de uma movimentação
        // arquivada (pra só olhar) — os relacionamentos acessados abaixo (categoria por id,
        // contribuintes, criadoPor/atualizadoPor) já são seguros com lazy-load normal, sem
        // precisar do JOIN FETCH de buscarPorIdComRelacoes.
        MovimentacaoFinanceira m = repository.findByIdAndIgrejaIdIncluindoArquivadas(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada."));
        String categoriaNome = categoriaService
                .mapaNomesIncluindoArquivadas(List.of(m.getCategoria().getId()), igrejaId)
                .get(m.getCategoria().getId());
        var pessoasContribuintes = resolverPessoasContribuintes(List.of(m));
        return MovimentacaoResponse.de(m, categoriaNome, pessoasContribuintes);
    }

    @Transactional
    public MovimentacaoResponse cadastrar(MovimentacaoRequestDTO dto, UUID igrejaId, UUID usuarioId) {
        log.info("Cadastrando movimentação. tipo={}, valor={}, categoria_id={}, contribuintes={}, criado_por={}, igreja_id={}",
                dto.tipo(), dto.valor(), dto.categoriaId(), dto.contribuintes().size(), usuarioId, igrejaId);

        CategoriaFinanceira categoria = categoriaService.buscarEntidade(dto.categoriaId(), igrejaId);
        validarCompatibilidade(dto.tipo(), categoria);
        validarContribuintes(dto.valor(), dto.contribuintes());

        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .categoria(categoria)
                .criadoPor(usuarioRepository.getReferenceById(usuarioId))
                .tipo(dto.tipo())
                .valor(dto.valor())
                .dataMovimentacao(dto.dataMovimentacao())
                .descricao(dto.descricao())
                .build();
        mov.getContribuintes().addAll(resolverContribuintes(mov, dto.contribuintes(), igrejaId));

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
        validarContribuintes(dto.valor(), dto.contribuintes());

        mov.setCategoria(categoria);
        mov.getContribuintes().clear();
        // Flush força o DELETE dos contribuintes órfãos antes do INSERT dos novos — sem isto,
        // Hibernate executa inserts antes de deletes na mesma flush e uma pessoa que continua
        // como contribuinte (mesmo movimentacao_id + pessoa_id) colide com a constraint UNIQUE.
        repository.flush();
        mov.getContribuintes().addAll(resolverContribuintes(mov, dto.contribuintes(), igrejaId));
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
        log.info("Arquivando movimentação. id={}, igreja_id={}", id, igrejaId);
        int linhas = repository.arquivarPorId(id, igrejaId);
        if (linhas == 0) {
            log.warn("Tentativa de arquivar movimentação inexistente. id={}, igreja_id={}", id, igrejaId);
            throw new ResourceNotFoundException("Movimentação não encontrada.");
        }
        outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.REMOVIDO,
                id,
                igrejaId
        );
        log.info("Movimentação arquivada. id={}, igreja_id={}", id, igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoArquivadaResponse> listarArquivadas(UUID igrejaId) {
        return repository.findArquivadasPorIgreja(igrejaId).stream()
                .map(m -> MovimentacaoArquivadaResponse.de(m, repository.contarContribuintes(m.getId()) > 0))
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        int linhas = repository.restaurarPorId(id, igrejaId);
        if (linhas == 0) {
            throw new ResourceNotFoundException("Movimentação não encontrada.");
        }
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.ATUALIZADO, id, igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);
    }

    /**
     * Diferente de Categoria: é a própria linha financeira, não uma referência de terceiro —
     * nunca bloqueia. Contribuintes são filhos dela (ON DELETE CASCADE) e somem junto; o aviso
     * já avisa isso antes de confirmar.
     */
    @Transactional
    public void excluirDefinitivo(UUID id, UUID igrejaId) {
        repository.findByIdAndIgrejaIdIncluindoArquivadas(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada."));
        repository.hardDeleteById(id);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.REMOVIDO, id, igrejaId);
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

    private Pessoa resolverMembro(UUID pessoaId, UUID igrejaId) {
        return membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> {
                    log.warn("Pessoa informado na movimentação não encontrado na igreja. pessoa_id={}, igreja_id={}", pessoaId, igrejaId);
                    return new ResourceNotFoundException("Pessoa não encontrado.");
                });
    }

    private void validarContribuintes(BigDecimal valorTotal, List<ContribuinteDTO> contribuintes) {
        if (contribuintes.isEmpty()) return;

        // Exatamente um entre pessoaId/nomeExterno por contribuinte — pessoa de fora (sem
        // cadastro) não tem um identificador único de verdade pra checar duplicidade contra
        // (dois "João" de fora são só uma coincidência de nome, não a mesma linha), então a
        // checagem de duplicado só faz sentido entre pessoaId's.
        Set<UUID> pessoas = new HashSet<>();
        for (ContribuinteDTO c : contribuintes) {
            boolean temPessoa = c.pessoaId() != null;
            boolean temNomeExterno = c.nomeExterno() != null && !c.nomeExterno().isBlank();
            if (temPessoa == temNomeExterno) {
                throw new BusinessException("CONTRIBUINTE_INVALIDO",
                        "Cada contribuinte precisa ser uma pessoa cadastrada OU um nome de fora, nunca os dois nem nenhum.");
            }
            if (temPessoa && !pessoas.add(c.pessoaId())) {
                throw new BusinessException("CONTRIBUINTE_DUPLICADO",
                        "A mesma pessoa não pode aparecer duas vezes na lista de contribuintes.");
            }
        }

        BigDecimal soma = contribuintes.stream()
                .map(ContribuinteDTO::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (soma.compareTo(valorTotal) != 0) {
            throw new BusinessException("VALOR_CONTRIBUINTES_DIVERGENTE",
                    "A soma dos contribuintes (" + soma + ") não bate com o valor total da movimentação (" + valorTotal + ").");
        }
    }

    private List<MovimentacaoContribuinte> resolverContribuintes(
            MovimentacaoFinanceira mov, List<ContribuinteDTO> dtos, UUID igrejaId) {
        return dtos.stream()
                .map(c -> MovimentacaoContribuinte.builder()
                        .movimentacao(mov)
                        .pessoa(c.pessoaId() != null ? resolverMembro(c.pessoaId(), igrejaId) : null)
                        .nomeExterno(c.pessoaId() == null ? c.nomeExterno().trim() : null)
                        .valor(c.valor())
                        .build())
                .toList();
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
    public void reindexarPorMembro(UUID pessoaId, UUID igrejaId) {
        List<UUID> ids = repository.buscarIdsPorMembro(pessoaId, igrejaId);
        if (ids.isEmpty()) return;
        log.info("Reindexando {} movimentações por alteração no membro. pessoa_id={}, igreja_id={}",
                ids.size(), pessoaId, igrejaId);
        ids.forEach(id -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO,
                TipoEventoOutbox.ATUALIZADO,
                id,
                igrejaId
        ));
    }
}