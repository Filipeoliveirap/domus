package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.modules.pagamento.PagamentoPollingService;
import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaCheckoutDTO;
import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaPublicaDTO;
import com.domus.api.modules.pagamento.cobranca.DTOs.PagarCobrancaRequest;
import com.domus.api.modules.pagamento.cobranca.DTOs.PagarCobrancaResponse;
import com.domus.api.modules.pagamento.cobranca.DTOs.OpcoesPagamentoResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Rota pública (sem autenticação) consumida pela página de checkout do pagador —
 * ver Task 14 no front. Por ser pública, o DTO devolvido carrega estritamente o
 * necessário para montar a tela: nunca telefone/e-mail/outros campos de Pessoa além do
 * nome.
 *
 * <p><b>Decisão sobre {@code POST /cobrancas/{id}/pagar} (Task 14, lacuna do plano):</b>
 * o endpoint é único pros dois fluxos (titular pagando na hora, logo após se inscrever, e
 * terceiro pagando pelo link público) e é <b>sem autenticação</b>, de propósito — pela
 * mesma razão que o {@code GET} acima já é público: {@code CobrancaEvento.id} é gerado por
 * {@code GenerationType.UUID} (UUIDv4, 122 bits de aleatoriedade), a mesma garantia de
 * "posse prova identidade" que já vale pro {@code tokenLinkPublico} (32 bytes aleatórios).
 * Exigir sessão aqui não fecharia brecha nenhuma — quem chama já possui o id (devolvido na
 * criação da inscrição, pro titular; devolvido dentro de {@link CobrancaPublicaDTO} depois
 * de resolver o {@code token} da URL, pro link público) — e forçaria duplicar toda a lógica
 * num endpoint autenticado só pra cobrir o caso do link, que por definição não tem sessão
 * (é aberto numa aba anônima, sem login). Manter incoerência de auth (metade dos casos com
 * cookie, metade sem) custaria mais superfície de bug do que a unificação sem auth.</p>
 *
 * <p>Este endpoint só <b>inicia</b> o pagamento no Mercado Pago (chama
 * {@link MercadoPagoClient#criarPagamentoComToken}); a confirmação definitiva (marcar a
 * {@code CobrancaEvento} como PAGO) continua vindo, de forma assíncrona, do webhook
 * (Task 10) — evita duplicar aqui a lógica de confirmação que já existe lá.</p>
 */
@RestController
@RequestMapping("/cobrancas")
public class CobrancaController {

    private final CobrancaEventoService service;
    private final CobrancaEventoRepository cobrancaRepository;
    private final EventoRepository eventoRepository;
    private final com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository;
    private final PessoaRepository pessoaRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final com.domus.api.modules.pagamento.CalculadoraTaxaPagamento calculadoraTaxaPagamento;
    private final PagamentoPollingService pagamentoPollingService;
    private final com.domus.api.modules.evento.inscricao.InscricaoService inscricaoService;
    private final com.domus.api.modules.pagamento.webhook.MercadoPagoWebhookService webhookService;
    private final com.domus.api.shared.security.UsuarioAutenticado usuarioAutenticado;

    public CobrancaController(CobrancaEventoService service,
                               CobrancaEventoRepository cobrancaRepository,
                               EventoRepository eventoRepository,
                               com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository,
                               PessoaRepository pessoaRepository,
                               MercadoPagoClient mercadoPagoClient,
                               com.domus.api.modules.pagamento.CalculadoraTaxaPagamento calculadoraTaxaPagamento,
                               PagamentoPollingService pagamentoPollingService,
                               com.domus.api.modules.evento.inscricao.InscricaoService inscricaoService,
                               com.domus.api.modules.pagamento.webhook.MercadoPagoWebhookService webhookService,
                               com.domus.api.shared.security.UsuarioAutenticado usuarioAutenticado) {
        this.service = service;
        this.cobrancaRepository = cobrancaRepository;
        this.eventoRepository = eventoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.pessoaRepository = pessoaRepository;
        this.mercadoPagoClient = mercadoPagoClient;
        this.calculadoraTaxaPagamento = calculadoraTaxaPagamento;
        this.pagamentoPollingService = pagamentoPollingService;
        this.inscricaoService = inscricaoService;
        this.webhookService = webhookService;
        this.usuarioAutenticado = usuarioAutenticado;
    }

    @GetMapping("/id/{id}")
    public CobrancaCheckoutDTO buscarPorId(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));

        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));

        String nomePagador;
        if (cobranca.getPessoaId() != null) {
            nomePagador = pessoaRepository.findById(cobranca.getPessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        } else {
            // Convidado sem cadastro (Plano 4b) — resolvido só pela InscricaoEvento.
            nomePagador = inscricaoRepository.findById(cobranca.getInscricaoId())
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição da cobrança não encontrada."))
                .getNomeConvidado();
        }

        return new CobrancaCheckoutDTO(
            cobranca.getId(),
            evento.getId(),
            evento.getTitulo(),
            evento.getInicioEm(),
            nomePagador,
            cobranca.getValor(),
            cobranca.getStatus().name(),
            cobranca.getExpiraEm(),
            cobranca.getMpPaymentId() != null
        );
    }

    @GetMapping("/{token}")
    public CobrancaPublicaDTO buscar(@PathVariable String token) {
        var cobranca = service.buscarPorToken(token);

        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));

        String nomePagador;
        if (cobranca.getPessoaId() != null) {
            nomePagador = pessoaRepository.findById(cobranca.getPessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        } else {
            // Convidado sem cadastro (Plano 4b) — resolvido só pela InscricaoEvento.
            nomePagador = inscricaoRepository.findById(cobranca.getInscricaoId())
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição da cobrança não encontrada."))
                .getNomeConvidado();
        }

        return new CobrancaPublicaDTO(
            cobranca.getId(),
            evento.getTitulo(),
            nomePagador,
            cobranca.getValor(),
            cobranca.getStatus().name(),
            cobranca.getExpiraEm(),
            cobranca.getMpPaymentId() != null
        );
    }

    @PostMapping("/{id}/pagar")
    @Transactional
    public PagarCobrancaResponse pagar(@PathVariable UUID id,
                                       @jakarta.validation.Valid @RequestBody PagarCobrancaRequest request) {
        // Achado em revisão de segurança (2026-08-26): lock pessimista na PRIMEIRA leitura,
        // não só no evento — sem isto, duas requisições quase simultâneas (duplo clique,
        // retry de rede) liam mpPaymentId == null antes de qualquer uma travar nada, e as
        // duas criavam um pagamento de verdade no Mercado Pago pra mesma cobrança (a
        // proteção "Critical 5" abaixo só barra o caso sequencial). Travando aqui, a segunda
        // requisição bloqueia até a primeira commitar e só então lê o mpPaymentId já
        // gravado — cai certinho no COBRANCA_JA_EM_PROCESSAMENTO.
        var cobranca = cobrancaRepository.buscarComLock(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));

        if (cobranca.getStatus() != StatusCobranca.PENDENTE) {
            throw new BusinessException("COBRANCA_NAO_PENDENTE",
                "Esta cobrança já foi paga, cancelada ou não está mais disponível para pagamento.");
        }
        // Critical 5 (revisão final de branch): sem isto, clicar "pagar" duas vezes (ou a
        // requisição ser reenviada antes do webhook confirmar) criava um SEGUNDO pagamento
        // no Mercado Pago pra mesma cobrança — cobrança duplicada real do pagador.
        // mpPaymentId é gravado (ver registrarTentativaPagamento) assim que a 1ª tentativa
        // cria o pagamento com sucesso, mesmo a cobrança continuando PENDENTE até o webhook.
        if (cobranca.getMpPaymentId() != null) {
            throw new BusinessException("COBRANCA_JA_EM_PROCESSAMENTO",
                "Já existe um pagamento em andamento para esta cobrança.");
        }
        if (cobranca.getExpiraEm().isBefore(Instant.now())) {
            throw new BusinessException("COBRANCA_EXPIRADA",
                "O prazo para pagar esta cobrança expirou.");
        }

        // A vaga só é reservada a partir daqui — clicar "Se inscrever" e ficar navegando
        // no checkout, sem enviar nada, não segura vaga de ninguém (ver
        // CobrancaEventoRepository.contarPessoasComVagaReservada). Lock no evento serializa
        // duas tentativas de pagamento concorrentes pra mesma última vaga: quem chega
        // primeiro aqui reserva; a segunda é recusada antes de chamar o Mercado Pago.
        var evento = eventoRepository.buscarComLock(cobranca.getEventoId(), cobranca.getIgrejaId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        if (evento.getVagas() != null) {
            long ocupadas = cobrancaRepository.contarPessoasComVagaReservada(evento.getId(), Instant.now());
            if (ocupadas >= evento.getVagas()) {
                throw new BusinessException("VAGAS_ESGOTADAS",
                    "As vagas deste evento estão esgotadas.");
            }
        }

        // Recálculo do valor no back (gross-up por meio/parcela) — o valor cobrado NUNCA
        // vem do front. As portas de cartão (aceita? teto de parcelas?) são do evento;
        // PIX_NAO_PARCELA / PARCELAS_INVALIDAS já são lançados de dentro de valorACobrar.
        if (request.meio() == com.domus.api.modules.pagamento.MeioPagamento.CARTAO
                && !evento.isPagamentoAceitaCartao()) {
            throw new BusinessException("CARTAO_NAO_ACEITO", "Este evento aceita apenas Pix.");
        }
        if (request.meio() == com.domus.api.modules.pagamento.MeioPagamento.CARTAO
                && request.parcelas() > evento.getPagamentoMaxParcelas()) {
            throw new BusinessException("PARCELAS_ACIMA_DO_TETO",
                "Este evento aceita no máximo " + evento.getPagamentoMaxParcelas() + "x.");
        }

        java.math.BigDecimal valorACobrar = calculadoraTaxaPagamento.valorACobrar(
            cobranca.getIgrejaId(), cobranca.getValor(), request.meio(), request.parcelas());

        // Mínimos do Mercado Pago (total e por parcela): uma requisição forjada não pode
        // passar por aqui com uma faixa que o MP recusaria — a UI já não oferece, mas o
        // servidor é quem garante. Mesma checagem que filtra as faixas em montarOpcoes.
        if (request.meio() == com.domus.api.modules.pagamento.MeioPagamento.CARTAO) {
            if (!service.acimaDoValorMinimoCartao(valorACobrar)) {
                throw new BusinessException("CARTAO_VALOR_MINIMO",
                    "Este valor não permite pagamento com cartão.");
            }
            if (!service.cartaoViavelParaValor(valorACobrar, request.parcelas())) {
                throw new BusinessException("PARCELAS_INVIAVEIS_PARA_VALOR",
                    "Este valor não permite parcelar em " + request.parcelas() + "x.");
            }
        }

        cobranca.registrarValorCobrado(valorACobrar);

        // O número de parcelas enviado ao Mercado Pago é o `parcelas` já validado (contra o
        // teto do evento e o gross-up), NUNCA o `installments` cru do Brick — senão dava pra
        // mandar parcelas=1 (gross-up barato, passa no teto) + installments=12 e a igreja
        // comeria o custo de 12x. `request.installments()` fica ignorado no servidor.
        var resultado = mercadoPagoClient.criarPagamentoComToken(
            cobranca.getIgrejaId(), cobranca, valorACobrar,
            request.token(), request.paymentMethodId(), request.parcelas(), request.payerEmail(), request.issuerId());

        cobranca.registrarTentativaPagamento(resultado.mpPaymentId());
        cobrancaRepository.save(cobranca);

        // Corre em paralelo ao webhook (não no lugar dele) — só reduz a espera percebida
        // pelo usuário quando o webhook demora. Ver PagamentoPollingService.
        pagamentoPollingService.pollarConfirmacao(cobranca.getIgrejaId(), cobranca.getId().toString(), resultado.mpPaymentId());

        return new PagarCobrancaResponse(resultado.mpPaymentId(), resultado.status(), resultado.statusDetail(),
            resultado.qrCode(), resultado.qrCodeBase64(), resultado.expiraEmPix());
    }

    /**
     * Pra o front saber quando fechar a tela de "aguardando Pix" — poll simples enquanto o
     * QR está na tela. Sem autenticação, pelo mesmo motivo já documentado na classe: o
     * {@code id} da cobrança já é a garantia de posse (UUIDv4), e exigir sessão aqui não
     * fecharia brecha nenhuma nem funcionaria pro link público (sem login).
     *
     * <p>Achado ao vivo (2026-09-08): o poll assíncrono do backend
     * ({@code PagamentoPollingService.pollarConfirmacao}) só cobre ~60s após o {@code /pagar};
     * um Pix pago depois disso — e sem o webhook do Mercado Pago chegar (ou chegando muito
     * atrasado) — deixava a cobrança PENDENTE pra sempre, com a pessoa travada em
     * "Confirmando pagamento…". Como o front chama este endpoint de poucos em poucos segundos
     * enquanto a tela está aberta, aproveita a chamada pra reconferir no Mercado Pago quando
     * ainda está PENDENTE. Idempotente ({@code confirmarPagamento}); falha de rede não
     * propaga — cai no status do banco.</p>
     */
    @GetMapping("/{id}/status")
    public StatusCobrancaResponse status(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));

        if (cobranca.getStatus() == StatusCobranca.PENDENTE && cobranca.getMpPaymentId() != null) {
            pagamentoPollingService.reconferirAgora(
                cobranca.getIgrejaId(), id.toString(), cobranca.getMpPaymentId());
            cobranca = cobrancaRepository.findById(id).orElse(cobranca);
        }
        return new StatusCobrancaResponse(cobranca.getStatus().name());
    }

    public record StatusCobrancaResponse(String status) {}

    /**
     * Opções de pagamento (Pix + faixas de cartão) pra a tela de escolha de método do
     * checkout. Sem autenticação, mesmo motivo do resto da classe (posse do UUID da
     * cobrança). O valor de cada opção é recalculado no back — o front só renderiza.
     */
    @GetMapping("/{id}/opcoes-pagamento")
    public OpcoesPagamentoResponse opcoesPagamento(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));
        return service.montarOpcoes(cobranca.getValor(), evento.isPagamentoAceitaCartao(),
            evento.getPagamentoMaxParcelas(), cobranca.getIgrejaId());
    }

    /**
     * Recupera o QR/copia-e-cola de um pagamento Pix em andamento — pro caso de a pessoa
     * dar reload na tela de checkout enquanto o Pix está pendente (achado ao vivo,
     * 2026-08-27): sem isto, a tela cai direto em "Confirmando pagamento…" sem nenhum jeito
     * de voltar a mostrar o QR. Sem autenticação, mesmo motivo já documentado na classe.
     *
     * <p>{@code qrCode}/{@code qrCodeBase64} nulos = o pagamento em andamento é cartão, não
     * Pix (cartão nunca tem QR). 404 = não existe nenhuma tentativa de pagamento ainda
     * ({@code mpPaymentId} nulo) — o front só chama este endpoint quando
     * {@code pagamentoEmAndamento} já é {@code true}, então isso não deveria acontecer no
     * uso normal, mas fica como resposta honesta em vez de um objeto com tudo nulo.</p>
     */
    @GetMapping("/{id}/pix")
    public PixResponse pix(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        if (cobranca.getMpPaymentId() == null) {
            throw new ResourceNotFoundException("Não há pagamento em andamento para esta cobrança.");
        }
        var qrCodePix = mercadoPagoClient.buscarQrCodePix(cobranca.getIgrejaId(), cobranca.getMpPaymentId());
        return new PixResponse(qrCodePix.qrCode(), qrCodePix.qrCodeBase64(), qrCodePix.expiraEm());
    }

    /** {@code expiraEm} é a validade real deste Pix específico, não o prazo da cobrança
     *  inteira — ver {@code PagarCobrancaResponse.expiraEmPix}. */
    public record PixResponse(String qrCode, String qrCodeBase64, java.time.Instant expiraEm) {}

    /**
     * "Gerar novo QR code" / "Pagar com outro método" — achado ao vivo (2026-08-27): uma
     * tentativa de Pix presa (QR escaneado mas nunca pago, ou simplesmente abandonado) não
     * dava nenhum jeito de tentar de novo antes de expirar sozinha (até 30min) — a pessoa
     * ficava travada na mesma tela sem opção. Cancela a tentativa no Mercado Pago
     * (best-effort — ver {@code MercadoPagoApi.cancelarPagamento}) e libera a cobrança pra
     * uma nova tentativa, com QUALQUER meio (não precisa ser Pix de novo). Sem autenticação,
     * mesmo motivo já documentado na classe.
     */
    @PostMapping("/{id}/reiniciar")
    @Transactional
    public void reiniciar(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.buscarComLock(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        if (cobranca.getStatus() != StatusCobranca.PENDENTE || cobranca.getMpPaymentId() == null) {
            // Nada pra reiniciar — cobrança já resolvida, ou nunca teve tentativa em andamento.
            return;
        }
        // [C3] reconfere no MP antes de descartar o mpPaymentId: se o pagamento aprovou na
        // janela entre o poll e o clique, confirmar em vez de perder o rastro (senão vira
        // pagamento órfão / risco de a pessoa pagar de novo). Se o cancelamento no MP
        // falhar, `cancelarPagamento` lança e a cobrança NÃO é liberada (o front mostra o erro).
        var info = mercadoPagoClient.buscarInformacoesPagamento(cobranca.getIgrejaId(), cobranca.getMpPaymentId());
        if ("approved".equals(info.status())) {
            webhookService.confirmarPagamento(id.toString(), cobranca.getMpPaymentId(), info);
            return;
        }
        mercadoPagoClient.cancelarPagamento(cobranca.getIgrejaId(), cobranca.getMpPaymentId());
        cobranca.liberarParaNovaTentativa();
        cobrancaRepository.save(cobranca);
    }

    /**
     * "Cancelar inscrição" do e-mail de lembrete de pagamento pendente — sem autenticação,
     * mesmo motivo já documentado na classe (o {@code id} da cobrança é a prova de posse).
     * Só cancela quando a inscrição ainda está AGUARDANDO_PAGAMENTO (ver
     * {@code InscricaoService.cancelarPorCobranca}) — um link velho não faz nada.
     */
    @PostMapping("/{id}/cancelar-inscricao")
    public void cancelarInscricao(@PathVariable UUID id) {
        inscricaoService.cancelarPorCobranca(id);
    }

    /**
     * Retry manual da tag "Estorno pendente" (2026-08-27) — ao contrário do resto desta
     * classe, este endpoint EXIGE sessão de ADMIN/LÍDER: é uma ação de gestão (mexe com o
     * dinheiro de outra pessoa a pedido do admin), não uma ação do próprio pagador provando
     * posse do id da cobrança. Ver {@code InscricaoService.tentarEstornoNovamente} e
     * {@code SecurityConfig} (matcher específico, ANTES do permitAll de {@code /cobrancas/**}).
     */
    @PostMapping("/{id}/tentar-estorno-novamente")
    public void tentarEstornoNovamente(@PathVariable UUID id) {
        inscricaoService.tentarEstornoNovamente(id, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getRole());
    }
}
