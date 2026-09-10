package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap achado em revisão (2026-08-26): pagamento/estorno de evento pago não aparecia em
 * lugar nenhum do financeiro da igreja — {@code CobrancaEvento} e
 * {@code MovimentacaoFinanceira} eram tabelas totalmente desconectadas. Este service é a
 * ponte, chamada por {@code MercadoPagoWebhookService} (pagamento aprovado) e
 * {@code InscricaoService} (estorno em cancelamento) — nenhum dos dois módulos de pagamento
 * conhece o modelo de categoria/movimentação além do que expõe aqui.
 *
 * <p>Decisão do usuário (2026-08-26): a categoria é resolvida automaticamente, tolerando
 * variações de nome já cadastradas pela igreja (singular/plural, maiúsculas — ver
 * {@link #NOMES_CATEGORIA_ACEITOS}) antes de criar uma nova chamada "Eventos". Quando uma
 * categoria nova é criada, ADMIN_IGREJA e quem tem a capacidade TESOUREIRO são notificados
 * (transparência: a igreja precisa saber que uma categoria nova apareceu sozinha). Estorno
 * gera uma SAÍDA espelhando a entrada original (não apaga/reverte) — preserva o rastro de
 * que houve um pagamento e depois um cancelamento, igual a qualquer outro estorno no
 * financeiro.
 */
@Service
public class MovimentacaoAutomaticaService {

    private static final Logger log = LoggerFactory.getLogger(MovimentacaoAutomaticaService.class);

    private static final String NOME_CATEGORIA_PADRAO = "Eventos";
    private static final Set<String> NOMES_CATEGORIA_ACEITOS = Set.of("evento", "eventos");

    private static final String NOME_CATEGORIA_TAXA = "Taxas de pagamento";
    private static final Set<String> NOMES_CATEGORIA_TAXA_ACEITOS = Set.of("taxa de pagamento", "taxas de pagamento");

    private static final List<String> PREFIXOS_DESCRICAO = List.of("Pagamento de inscrição — ", "Reembolso — ");

    private final CategoriaFinanceiraRepository categoriaRepository;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final OutboxRegistrador outboxRegistrador;
    private final CacheEvictor cacheEvictor;

    public MovimentacaoAutomaticaService(CategoriaFinanceiraRepository categoriaRepository,
                                          MovimentacaoFinanceiraRepository movimentacaoRepository,
                                          IgrejaRepository igrejaRepository,
                                          PessoaRepository pessoaRepository,
                                          UsuarioRepository usuarioRepository,
                                          NotificacaoService notificacaoService,
                                          OutboxRegistrador outboxRegistrador,
                                          CacheEvictor cacheEvictor) {
        this.categoriaRepository = categoriaRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.igrejaRepository = igrejaRepository;
        this.pessoaRepository = pessoaRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacaoService = notificacaoService;
        this.outboxRegistrador = outboxRegistrador;
        this.cacheEvictor = cacheEvictor;
    }

    /**
     * Chamado quando um pagamento de evento é confirmado — entrada bruta na categoria de
     * eventos e, em seguida, a taxa cobrada pelo Mercado Pago como SAÍDA na categoria
     * "Taxas de pagamento" (só quando {@code taxaMp > 0}). Os dois lançamentos separados
     * deixam o financeiro da igreja bater com o extrato: o bruto é o que o inscrito pagou,
     * a taxa é o que o gateway reteve.
     */
    @Transactional
    public void registrarEntradaDeEvento(UUID igrejaId, BigDecimal valorBruto, BigDecimal taxaMp,
                                          String descricao, UUID pessoaId, String nomePagador) {
        registrar(igrejaId, TipoMovimentacao.ENTRADA, valorBruto, descricao, pessoaId, nomePagador,
            buscarOuCriarCategoria(igrejaId, NOME_CATEGORIA_PADRAO, NOMES_CATEGORIA_ACEITOS, TipoCategoria.AMBOS));
        registrarTaxa(igrejaId, TipoMovimentacao.SAIDA, taxaMp, "Taxa Mercado Pago — " + semPrefixo(descricao));
    }

    /**
     * Chamado quando um pagamento de evento é estornado — saída bruta espelhando a entrada
     * e, quando houve devolução de taxa pelo gateway, uma ENTRADA na categoria
     * "Taxas de pagamento" (só quando {@code taxaDevolvida > 0}).
     */
    @Transactional
    public void registrarSaidaDeEvento(UUID igrejaId, BigDecimal valorBruto, BigDecimal taxaDevolvida,
                                        String descricao, UUID pessoaId, String nomePagador) {
        registrar(igrejaId, TipoMovimentacao.SAIDA, valorBruto, descricao, pessoaId, nomePagador,
            buscarOuCriarCategoria(igrejaId, NOME_CATEGORIA_PADRAO, NOMES_CATEGORIA_ACEITOS, TipoCategoria.AMBOS));
        registrarTaxa(igrejaId, TipoMovimentacao.ENTRADA, taxaDevolvida,
            "Devolução de taxa — " + semPrefixo(descricao));
    }

    private void registrar(UUID igrejaId, TipoMovimentacao tipo, BigDecimal valor, String descricao,
                            UUID pessoaId, String nomePagador, CategoriaFinanceira categoria) {
        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
            .igreja(igrejaRepository.getReferenceById(igrejaId))
            .categoria(categoria)
            .criadoPorTexto("Sistema (pagamento de evento)")
            .tipo(tipo)
            .valor(valor)
            .dataMovimentacao(LocalDate.now())
            .descricao(descricao)
            .build();

        // Contribuinte/beneficiário sempre entra — com pessoa cadastrada quando existe, ou só
        // o nome (convidado sem cadastro) quando não. Sem isso, quem paga sem cadastro só
        // aparecia no texto da descrição, nunca na coluna dedicada nem no relatório "por
        // contribuinte" (achado revisando com o usuário, 2026-08-26).
        mov.getContribuintes().add(pessoaId != null
            ? MovimentacaoContribuinte.builder().movimentacao(mov)
                .pessoa(pessoaRepository.getReferenceById(pessoaId)).valor(valor).build()
            : MovimentacaoContribuinte.builder().movimentacao(mov)
                .nomeExterno(nomePagador).valor(valor).build());

        movimentacaoRepository.save(mov);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.CRIADO, mov.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);

        log.info("Movimentação automática de evento registrada. tipo={} valor={} categoria_id={} igreja_id={}",
            tipo, valor, categoria.getId(), igrejaId);
    }

    /**
     * Lança a taxa do gateway como movimentação própria na categoria "Taxas de pagamento"
     * (sem contribuinte — não é dinheiro de ninguém em particular, é custo operacional).
     * Nada acontece quando não há taxa a lançar ({@code null} ou {@code <= 0}).
     */
    private void registrarTaxa(UUID igrejaId, TipoMovimentacao tipo, BigDecimal valor, String descricao) {
        if (valor == null || valor.signum() <= 0) return;

        CategoriaFinanceira categoria = buscarOuCriarCategoria(
            igrejaId, NOME_CATEGORIA_TAXA, NOMES_CATEGORIA_TAXA_ACEITOS, TipoCategoria.SAIDA);

        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
            .igreja(igrejaRepository.getReferenceById(igrejaId))
            .categoria(categoria)
            .criadoPorTexto("Sistema (taxa de pagamento)")
            .tipo(tipo)
            .valor(valor)
            .dataMovimentacao(LocalDate.now())
            .descricao(descricao)
            .build();

        movimentacaoRepository.save(mov);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.CRIADO, mov.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);

        log.info("Movimentação automática de taxa de pagamento registrada. tipo={} valor={} igreja_id={}",
            tipo, valor, igrejaId);
    }

    /** Tira o prefixo de fluxo ("Pagamento de inscrição — " / "Reembolso — ") pra sobrar
     *  só "&lt;evento&gt; (&lt;pagador&gt;)" na descrição da taxa. Sem prefixo casando, usa a descrição inteira. */
    private static String semPrefixo(String descricao) {
        if (descricao == null) return null;
        for (String prefixo : PREFIXOS_DESCRICAO) {
            if (descricao.startsWith(prefixo)) return descricao.substring(prefixo.length());
        }
        return descricao;
    }

    private CategoriaFinanceira buscarOuCriarCategoria(UUID igrejaId, String nomePadrao,
                                                       Set<String> nomesAceitos, TipoCategoria tipo) {
        var existentes = categoriaRepository.buscarPorIgrejaENomeNormalizado(igrejaId, nomesAceitos);
        if (!existentes.isEmpty()) return existentes.get(0);

        CategoriaFinanceira nova = categoriaRepository.save(CategoriaFinanceira.builder()
            .igreja(igrejaRepository.getReferenceById(igrejaId))
            .nome(nomePadrao)
            .tipo(tipo)
            .build());

        log.info("Categoria financeira \"{}\" criada automaticamente. igreja_id={} categoria_id={}",
            nomePadrao, igrejaId, nova.getId());
        notificarCategoriaCriada(igrejaId, nomePadrao);

        return nova;
    }

    private void notificarCategoriaCriada(UUID igrejaId, String nomeCategoria) {
        Set<UUID> destinatarios = new HashSet<>();
        for (Usuario u : usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA")) {
            destinatarios.add(u.getId());
        }
        for (Usuario u : usuarioRepository.findByIgrejaIdAndCapacidadeAndAtivoTrue(igrejaId, "TESOUREIRO")) {
            destinatarios.add(u.getId());
        }

        for (UUID destinatarioId : destinatarios) {
            notificacaoService.criar(
                TipoNotificacao.CATEGORIA_FINANCEIRA_AUTO_CRIADA,
                igrejaId,
                destinatarioId,
                "A categoria financeira \"" + nomeCategoria + "\" foi criada automaticamente para os lançamentos de eventos pagos.",
                "/financeiro/categorias");
        }
    }
}
