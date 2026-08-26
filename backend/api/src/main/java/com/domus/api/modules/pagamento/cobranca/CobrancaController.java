package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.AcompanhanteRepository;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaCheckoutDTO;
import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaPublicaDTO;
import com.domus.api.modules.pagamento.cobranca.DTOs.PagarCobrancaRequest;
import com.domus.api.modules.pagamento.cobranca.DTOs.PagarCobrancaResponse;
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
 * necessário para montar a tela: nunca telefone/e-mail/outros campos de Pessoa ou
 * AcompanhanteInscricao além do nome.
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
    private final AcompanhanteRepository acompanhanteRepository;
    private final MercadoPagoClient mercadoPagoClient;

    public CobrancaController(CobrancaEventoService service,
                               CobrancaEventoRepository cobrancaRepository,
                               EventoRepository eventoRepository,
                               com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository,
                               PessoaRepository pessoaRepository,
                               AcompanhanteRepository acompanhanteRepository,
                               MercadoPagoClient mercadoPagoClient) {
        this.service = service;
        this.cobrancaRepository = cobrancaRepository;
        this.eventoRepository = eventoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.pessoaRepository = pessoaRepository;
        this.acompanhanteRepository = acompanhanteRepository;
        this.mercadoPagoClient = mercadoPagoClient;
    }

    @GetMapping("/id/{id}")
    public CobrancaCheckoutDTO buscarPorId(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));

        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));

        String nomePagador;
        String emailPagador = null;
        if (cobranca.getPessoaId() != null) {
            var pessoa = pessoaRepository.findById(cobranca.getPessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."));
            nomePagador = pessoa.getNome();
            emailPagador = pessoa.getEmail();
        } else if (cobranca.getAcompanhanteId() != null) {
            nomePagador = acompanhanteRepository.findById(cobranca.getAcompanhanteId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        } else {
            // Convidado sem cadastro (Plano 4b) — nem pessoa nem acompanhante, resolvido
            // só pela InscricaoEvento (nomeConvidado). Sem e-mail: não existe onde buscar
            // um pra convidado sem cadastro.
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
            emailPagador,
            cobranca.getValor(),
            cobranca.getStatus().name(),
            cobranca.getExpiraEm()
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
        } else if (cobranca.getAcompanhanteId() != null) {
            nomePagador = acompanhanteRepository.findById(cobranca.getAcompanhanteId())
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
            cobranca.getExpiraEm()
        );
    }

    @PostMapping("/{id}/pagar")
    @Transactional
    public PagarCobrancaResponse pagar(@PathVariable UUID id, @RequestBody PagarCobrancaRequest request) {
        var cobranca = cobrancaRepository.findById(id)
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

        var resultado = mercadoPagoClient.criarPagamentoComToken(
            cobranca.getIgrejaId(), cobranca,
            request.token(), request.paymentMethodId(), request.installments(), request.payerEmail(), request.issuerId());

        cobranca.registrarTentativaPagamento(resultado.mpPaymentId());
        cobrancaRepository.save(cobranca);

        return new PagarCobrancaResponse(resultado.mpPaymentId(), resultado.status(), resultado.qrCode(), resultado.qrCodeBase64());
    }

    /**
     * Pra o front saber quando fechar a tela de "aguardando Pix" — poll simples enquanto o
     * QR está na tela. Sem autenticação, pelo mesmo motivo já documentado na classe: o
     * {@code id} da cobrança já é a garantia de posse (UUIDv4), e exigir sessão aqui não
     * fecharia brecha nenhuma nem funcionaria pro link público (sem login).
     */
    @GetMapping("/{id}/status")
    public StatusCobrancaResponse status(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        return new StatusCobrancaResponse(cobranca.getStatus().name());
    }

    public record StatusCobrancaResponse(String status) {}
}
