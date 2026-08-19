package com.domus.api.modules.sync;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoDocument;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovimentacaoSincronizador implements SincronizadorEntidade {

    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final MovimentacaoSearchRepository movimentacaoSearchRepository;
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final PessoaRepository pessoaRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.MOVIMENTACAO;
    }

    @Override
    public void indexar(UUID entidadeId) {
        Optional<MovimentacaoFinanceira> mov = movimentacaoRepository.findById(entidadeId);
        mov.ifPresentOrElse(
                m -> {
                    String categoriaNome = categoriaRepository
                            .findByIdAndIgrejaIdIncluindoArquivadas(m.getCategoria().getId(), m.getIgreja().getId())
                            .map(c -> c.getNome())
                            .orElse(null);
                    var idsPessoa = m.getContribuintes().stream()
                            .map(c -> c.getPessoa())
                            .filter(p -> p != null)
                            .map(Pessoa::getId)
                            .distinct()
                            .toList();
                    Map<UUID, String> nomesPorPessoa = idsPessoa.isEmpty() ? Map.of()
                            : pessoaRepository.findByIdInIncluindoArquivadas(idsPessoa).stream()
                                    .collect(java.util.stream.Collectors.toMap(Pessoa::getId, Pessoa::getNome));
                    movimentacaoSearchRepository.save(MovimentacaoDocument.de(m, categoriaNome, nomesPorPessoa));
                    log.debug("Movimentação indexada no Elastic. id={}", entidadeId);
                },
                () -> {
                    movimentacaoSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Movimentação não encontrada no Postgres, removida do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        movimentacaoSearchRepository.deleteById(entidadeId.toString());
        log.debug("Movimentação removida do Elastic. id={}", entidadeId);
    }
}