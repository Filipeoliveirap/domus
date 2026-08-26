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

    /** Chamado quando um pagamento de evento é confirmado — entrada na categoria de eventos. */
    @Transactional
    public void registrarEntradaDeEvento(UUID igrejaId, BigDecimal valor, String descricao, UUID pessoaId) {
        registrar(igrejaId, TipoMovimentacao.ENTRADA, valor, descricao, pessoaId);
    }

    /** Chamado quando um pagamento de evento é estornado — saída espelhando a entrada. */
    @Transactional
    public void registrarSaidaDeEvento(UUID igrejaId, BigDecimal valor, String descricao, UUID pessoaId) {
        registrar(igrejaId, TipoMovimentacao.SAIDA, valor, descricao, pessoaId);
    }

    private void registrar(UUID igrejaId, TipoMovimentacao tipo, BigDecimal valor, String descricao, UUID pessoaId) {
        CategoriaFinanceira categoria = buscarOuCriarCategoriaEventos(igrejaId);

        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
            .igreja(igrejaRepository.getReferenceById(igrejaId))
            .categoria(categoria)
            .criadoPorTexto("Sistema (pagamento de evento)")
            .tipo(tipo)
            .valor(valor)
            .dataMovimentacao(LocalDate.now())
            .descricao(descricao)
            .build();

        if (pessoaId != null) {
            mov.getContribuintes().add(MovimentacaoContribuinte.builder()
                .movimentacao(mov)
                .pessoa(pessoaRepository.getReferenceById(pessoaId))
                .valor(valor)
                .build());
        }

        movimentacaoRepository.save(mov);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.CRIADO, mov.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);

        log.info("Movimentação automática de evento registrada. tipo={} valor={} categoria_id={} igreja_id={}",
            tipo, valor, categoria.getId(), igrejaId);
    }

    private CategoriaFinanceira buscarOuCriarCategoriaEventos(UUID igrejaId) {
        var existentes = categoriaRepository.buscarPorIgrejaENomeNormalizado(igrejaId, NOMES_CATEGORIA_ACEITOS);
        if (!existentes.isEmpty()) return existentes.get(0);

        CategoriaFinanceira nova = categoriaRepository.save(CategoriaFinanceira.builder()
            .igreja(igrejaRepository.getReferenceById(igrejaId))
            .nome(NOME_CATEGORIA_PADRAO)
            .tipo(TipoCategoria.AMBOS)
            .build());

        log.info("Categoria financeira \"{}\" criada automaticamente. igreja_id={} categoria_id={}",
            NOME_CATEGORIA_PADRAO, igrejaId, nova.getId());
        notificarCategoriaCriada(igrejaId);

        return nova;
    }

    private void notificarCategoriaCriada(UUID igrejaId) {
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
                "A categoria financeira \"" + NOME_CATEGORIA_PADRAO + "\" foi criada automaticamente para receber pagamentos de eventos pagos.",
                "/financeiro/categorias");
        }
    }
}
