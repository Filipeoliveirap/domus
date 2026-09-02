package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.elegibilidade.Elegibilidade;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.NaoElegivelException;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoService;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.evento.inscricao.DTOs.InscritoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ListaInscritosResponse;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ParticipanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.PessoaInscritaComCobranca;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ConflitoNegocioException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class InscricaoService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PessoaRepository membroRepository;
    private final UsuarioRepository usuarioRepository;
    private final VisitanteRepository visitanteRepository;
    private final ElegibilidadeService elegibilidadeService;
    private final FamiliaIgrejaService familiaIgrejaService;
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    private final com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository campoPersonalizadoRepository;
    private final com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository respostaCampoPersonalizadoRepository;
    private final CobrancaEventoService cobrancaEventoService;
    private final CobrancaEventoRepository cobrancaEventoRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final ContaPagamentoIgrejaRepository contaPagamentoIgrejaRepository;
    private final EmailService emailService;
    private final com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService movimentacaoAutomaticaService;

    /** Usado só pra montar o link de pagamento no e-mail de {@link #aplicarEventoVirouPago}
     *  ("gerarLink", igual ao convite pra terceiro pagar) — mesmo padrão de
     *  {@code ConviteController}/{@code PasswordResetService}. */
    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Important 9 (revisão final de branch): sem isto, uma inscrição em evento pago era
     * criada com sucesso mesmo sem a igreja ter conectado uma conta Mercado Pago — só
     * falhava depois, na hora de {@code /pagar} (checagem que já existia em
     * {@code MercadoPagoClient.obterAccessTokenPlano}). Checa ANTES de criar qualquer
     * registro (inscrição ou cobrança), pra a transação inteira reverter sem deixar
     * rastro — mesmo código de erro (`IGREJA_SEM_CONTA_PAGAMENTO`) que o pagamento já usa.
     */
    private void validarContaPagamentoConectada(UUID igrejaId) {
        if (contaPagamentoIgrejaRepository.findByIgrejaId(igrejaId).isEmpty()) {
            throw new BusinessException("IGREJA_SEM_CONTA_PAGAMENTO",
                    "Esta igreja ainda não conectou uma conta para receber pagamentos.");
        }
    }

    /** Auto-inscrição funciona em QUALQUER evento, independente de {@code requerInscricao}; evento buscado com lock. */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID pessoaId, UUID inscritoPorOuNull,
                                            UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        var resultado = inscreverInterno(eventoId, pessoaId, inscritoPorOuNull, minhaPessoaId, role,
                confirmado, igrejaId, false);
        return MinhaInscricaoResponse.from(resultado.inscricao(),
                resultado.cobranca() != null ? resultado.cobranca().getId() : null);
    }

    /**
     * Núcleo de {@link #inscrever}, com um parâmetro a mais: {@code gerarLinkSePago}
     * (Task 14, revisão pós-review). Sozinho, {@code inscrever} nunca oferece link — é
     * usado pela auto-inscrição, onde a regra "titular sempre paga agora" (Task 9) faz
     * sentido total. Mas {@link #inscreverPessoas} (lote do admin/líder) inscreve OUTRAS
     * pessoas, que não estão logadas nem presentes — nesse caso faz sentido permitir
     * "gerar link" pra quem está sendo inscrito pagar depois, sozinho. Ainda assim, a
     * pessoa que fez a ação (identificada por {@code minhaPessoaId}) nunca vira link para
     * si mesma, mesmo que apareça na própria lista do lote — ela está logada e presente,
     * então preserva a mesma trava de {@code inscrever}.
     */
    private record ResultadoInscricao(InscricaoEvento inscricao, CobrancaEvento cobranca) {}

    private ResultadoInscricao inscreverInterno(UUID eventoId, UUID pessoaId, UUID inscritoPorOuNull,
                                            UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId,
                                            boolean gerarLinkSePago) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Pessoa membro = membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        // E-mail é obrigatório em qualquer evento (2026-08-27, decisão do autor) — mesmo
        // motivo do convidado sem cadastro (ver inscreverConvidado): se o evento gratuito
        // virar pago depois, precisa de um jeito de avisar quem já está inscrito.
        if (membro.getEmail() == null || membro.getEmail().isBlank()) {
            throw new BusinessException("EMAIL_OBRIGATORIO",
                    "É necessário ter um e-mail cadastrado para se inscrever em eventos.");
        }

        if (evento.getPreco() != null) {
            validarContaPagamentoConectada(igrejaId);
        }

        validarEventoAberto(evento);
        boolean porExcecao = validarElegibilidade(evento, membro, role, confirmado, igrejaId);

        InscricaoEvento inscricao = inscricaoRepository
                .findByEventoIdAndPessoaId(eventoId, pessoaId)
                .orElse(null);

        if (inscricao != null && inscricao.estaConfirmada()) {
            String mensagem = inscritoPorOuNull == null
                    ? "Você já está inscrito neste evento."
                    : "Este membro já está inscrito no evento.";
            throw new BusinessException("JA_INSCRITO", mensagem);
        }

        validarVaga(evento, 1);

        // Evento pago: a inscrição nasce AGUARDANDO_PAGAMENTO — só vira CONFIRMADA quando
        // o webhook do Mercado Pago confirmar o pagamento (MercadoPagoWebhookService). A
        // reserva de vaga não depende deste status (ver contarOcupadas, baseada em
        // CobrancaEvento) — este status é só o que aparece pra quem lista inscritos.
        StatusInscricao statusInicial = evento.getPreco() != null
                ? StatusInscricao.AGUARDANDO_PAGAMENTO
                : StatusInscricao.CONFIRMADA;

        if (inscricao != null) {
            inscricao.setStatus(statusInicial);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
            inscricao.setInscritoPorExcecao(porExcecao);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .pessoa(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(statusInicial)
                    .inscritoPorExcecao(porExcecao)
                    .build();
        }

        InscricaoEvento salva = inscricaoRepository.save(inscricao);

        // Evento pago: titular sempre "eu pago agora" (nunca vira link, diferente do
        // convidado trazido via inscreverConvidado) — vaga fica reservada via CobrancaEvento
        // (ver validarVaga), não pela InscricaoEvento (que agora só confirma quando o
        // pagamento é aprovado, ver statusInicial acima). Quem assina como "criado por":
        // quem inscreveu (admin/líder em lote) ou, na auto-inscrição (inscritoPorOuNull
        // nulo), o próprio usuário do titular — sempre existe, pois só se auto-inscreve
        // quem está logado.
        CobrancaEvento cobranca = null;
        if (evento.getPreco() != null) {
            UUID criadoPorUsuarioId = inscritoPorOuNull != null
                    ? inscritoPorOuNull
                    : usuarioRepository.findByPessoaId(pessoaId).map(u -> u.getId()).orElse(null);
            // "Gerar link" só faz sentido pra quem NÃO é quem está fazendo a ação agora —
            // a própria pessoa logada continua travada em "paga agora" (Task 9), mesmo
            // quando aparece na lista de um lote que ela mesma está confirmando.
            boolean podeVirarLink = gerarLinkSePago && !pessoaId.equals(minhaPessoaId);
            cobranca = podeVirarLink
                    ? cobrancaEventoService.criarParaTerceiro(igrejaId, eventoId, salva.getId(), pessoaId,
                            evento.getPreco(), criadoPorUsuarioId, true)
                    : cobrancaEventoService.criarParaTitular(igrejaId, eventoId, salva.getId(), pessoaId,
                            evento.getPreco(), criadoPorUsuarioId);
        }

        // Avisa cada responsável — menos quem se auto-inscreveu e quem fez a inscrição
        // (esse já sabe da ação).
        for (var resp : evento.getResponsaveis()) {
            if (resp.getPessoa() == null || resp.getPessoa().getId().equals(pessoaId)) continue;
            usuarioRepository.findByPessoaId(resp.getPessoa().getId())
                    .filter(usuario -> !usuario.getId().equals(inscritoPorOuNull))
                    .ifPresent(usuario ->
                            notificacaoService.criar(
                                    com.domus.api.modules.notificacao.TipoNotificacao.INSCRICAO_EVENTO_RESPONSAVEL,
                                    igrejaId, usuario.getId(),
                                    membro.getNome() + " se inscreveu em " + evento.getTitulo() + ".",
                                    "/eventos/" + eventoId + "/inscritos"));
        }

        // Auto-inscrição já abre o modal de perguntas na hora, no próprio front — só quem
        // foi inscrito por OUTRA pessoa (lote do admin) precisa ser avisado, senão só
        // descobre a pendência se abrir o evento sozinho.
        if (inscritoPorOuNull != null) {
            notificarPendenciaDeCamposSeHouver(evento, membro, igrejaId, inscritoPorOuNull);
        }

        log.info("Inscrição confirmada. evento_id={}, pessoa_id={}, inscrito_por={}, igreja_id={}",
                eventoId, pessoaId, inscritoPorOuNull, igrejaId);
        return new ResultadoInscricao(salva, cobranca);
    }

    private void notificarPendenciaDeCamposSeHouver(Evento evento, Pessoa pessoaInscrita, UUID igrejaId, UUID usuarioIdAtor) {
        long totalObrigatorios = campoPersonalizadoRepository
                .findByEventoIdAndIgrejaIdOrderByOrdemAsc(evento.getId(), igrejaId).stream()
                .filter(com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEvento::isObrigatorio)
                .count();
        if (totalObrigatorios == 0) return;

        usuarioRepository.findByPessoaId(pessoaInscrita.getId())
                .filter(usuario -> !usuario.getId().equals(usuarioIdAtor))
                .ifPresent(usuario -> notificacaoService.criar(
                        com.domus.api.modules.notificacao.TipoNotificacao.CAMPO_PERSONALIZADO_PENDENTE,
                        igrejaId, usuario.getId(),
                        "Você foi inscrito em \"" + evento.getTitulo() + "\" — responda "
                                + (totalObrigatorios == 1 ? "1 pergunta pendente" : totalObrigatorios + " perguntas pendentes")
                                + " do evento.",
                        "/eventos?detalhe=" + evento.getId()));
    }

    /**
     * Overload de compatibilidade — mantém o comportamento anterior (ninguém vira link,
     * todo mundo "paga agora") para quem ainda chama sem escolher por pessoa. Os 21+
     * usos existentes (`InscricaoServiceTest`, `InscricaoController`) continuam valendo
     * sem alteração.
     */
    @Transactional
    public List<PessoaInscritaComCobranca> inscreverPessoas(UUID eventoId, List<UUID> pessoaIds,
                                 UUID inscritoPorUsuarioId, UUID minhaPessoaId, String role, boolean confirmado,
                                 UUID igrejaId) {
        return inscreverPessoas(eventoId, pessoaIds, java.util.Set.of(), inscritoPorUsuarioId, minhaPessoaId,
                role, confirmado, igrejaId);
    }

    /**
     * Task 14 (revisão pós-review): {@code pessoaIdsParaLink} é o subconjunto de
     * {@code pessoaIds} que a tela "Divisão de pagamento" (`EscolhaPagamentoPorPessoa`,
     * front) marcou como "gerar link" em vez de "eu pago agora" — cada uma dessas
     * recebe uma {@code CobrancaEvento} com {@code tokenLinkPublico}, pra pagar sozinha
     * depois, sem que quem está inscrevendo (admin/líder) precise pagar por ela na hora.
     * Tudo ou nada — checa os já inscritos em uma query só para nomear a quantidade no erro.
     */
    @Transactional
    public List<PessoaInscritaComCobranca> inscreverPessoas(UUID eventoId, List<UUID> pessoaIds,
                                 java.util.Set<UUID> pessoaIdsParaLink, UUID inscritoPorUsuarioId,
                                 UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarOrganizaInscricao(evento, "Este evento não organiza inscrição de outras pessoas.");
        validarEventoAberto(evento);

        if (!pessoaIds.isEmpty()) {
            List<UUID> jaInscritos = inscricaoRepository.listarPessoaIdsJaInscritos(eventoId, pessoaIds);
            if (!jaInscritos.isEmpty()) {
                String mensagem = jaInscritos.size() == 1
                        ? "Este membro já está inscrito no evento."
                        : jaInscritos.size() + " membros já estão inscritos no evento.";
                throw new BusinessException("JA_INSCRITO", mensagem);
            }
        }

        List<PessoaInscritaComCobranca> resultado = new ArrayList<>();
        for (UUID pessoaId : pessoaIds) {
            boolean gerarLink = pessoaIdsParaLink.contains(pessoaId);
            var r = inscreverInterno(eventoId, pessoaId, inscritoPorUsuarioId, minhaPessoaId, role, confirmado,
                    igrejaId, gerarLink);
            resultado.add(new PessoaInscritaComCobranca(
                    pessoaId,
                    r.inscricao().getId(),
                    r.cobranca() != null ? r.cobranca().getId() : null,
                    r.cobranca() != null ? r.cobranca().getTokenLinkPublico() : null));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID pessoaId) {
        return inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(i -> i.estaConfirmada() || i.estaAguardandoPagamento())
                .map(i -> MinhaInscricaoResponse.from(i, cobrancaPendenteDoTitular(i.getId(), pessoaId)))
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }

    /**
     * Task 14: se a pessoa recarregar a página antes de pagar (ou fechar o Brick sem
     * concluir), {@code minhaInscricao} precisa continuar devolvendo o id da cobrança
     * pendente do TITULAR — nunca a de um convidado, que segue um fluxo à parte
     * (link compartilhado, não o Brick embutido nesta tela).
     */
    private UUID cobrancaPendenteDoTitular(UUID inscricaoId, UUID pessoaId) {
        return cobrancaEventoRepository.findByInscricaoId(inscricaoId).stream()
                .filter(c -> pessoaId.equals(c.getPessoaId()))
                .filter(c -> c.getStatus() == StatusCobranca.PENDENTE)
                .map(CobrancaEvento::getId)
                .findFirst()
                .orElse(null);
    }

    /** {@code requerInscricao} não se aplica à auto-inscrição (ver {@link #inscrever}), que funciona em qualquer evento. */
    private void validarOrganizaInscricao(Evento evento, String mensagem) {
        if (!evento.isRequerInscricao()) {
            throw new BusinessException("INSCRICAO_NAO_HABILITADA", mensagem);
        }
    }

    /** Evento EM_ANDAMENTO ou ENCERRADO não aceita inscrição/convidado. */
    private void validarEventoAberto(Evento evento) {
        SituacaoEvento situacao = evento.getSituacao();
        if (situacao == SituacaoEvento.EM_ANDAMENTO) {
            throw new BusinessException("EVENTO_EM_ANDAMENTO",
                    "Este evento já começou. Não é mais possível se inscrever.");
        }
        if (situacao == SituacaoEvento.ENCERRADO) {
            throw new BusinessException("EVENTO_ENCERRADO",
                    "Este evento já aconteceu. Não é mais possível se inscrever.");
        }
    }

    /** Antes disto, buscar o mesmo visitante/pessoa de fora duas vezes no modal "Inscrever
     *  alguém" (ou no convite público) criava duas inscrições separadas, ocupando duas
     *  vagas pra mesma pessoa. Checa contra qualquer outro convidado sem cadastro já
     *  inscrito no evento (não existe mais a distinção "topo" vs. "acompanhante" — cada
     *  convidado é sua própria {@code InscricaoEvento}). */
    /** Mesma mensagem dupla de {@link #inscrever} (JA_INSCRITO): quem preenche o próprio
     *  formulário pelo link ({@code inscritoPorUsuarioId == null}) vê "você"; quem é
     *  cadastrado por outra pessoa pelo sistema vê "essa pessoa". */
    private void validarConvidadoTopoNaoDuplicado(UUID eventoId, String nome, String telefone,
                                                   UUID visitanteId, UUID inscritoPorUsuarioId) {
        List<InscricaoEvento> convidados = inscricaoRepository.listarConvidadosSemCadastroPorEvento(eventoId);

        // Com visitanteId, a checagem é exata (o mesmo Visitante já está inscrito) — não
        // depende de nome/telefone baterem, que é frágil (apelido, telefone desatualizado…).
        boolean duplicadoPorVisitante = visitanteId != null && convidados.stream()
                .anyMatch(i -> i.getVisitante() != null && i.getVisitante().getId().equals(visitanteId));

        String telefoneNovo = TextoUtil.somenteDigitos(telefone);
        String nomeNovo = TextoUtil.normalizarParaComparacao(nome);

        boolean duplicado = convidados.stream()
                .anyMatch(i -> mesmoConvidado(telefoneNovo, nomeNovo, i.getTelefoneConvidado(), i.getNomeConvidado()));

        if (duplicadoPorVisitante || duplicado) {
            String mensagem = inscritoPorUsuarioId == null
                    ? "Você já está inscrito neste evento."
                    : "Essa pessoa já está inscrita neste evento.";
            throw new BusinessException("CONVIDADO_DUPLICADO", mensagem);
        }
    }

    private boolean mesmoConvidado(String telefoneNovo, String nomeNovo, String telefoneExistente, String nomeExistente) {
        String telefoneExistenteNorm = TextoUtil.somenteDigitos(telefoneExistente);
        return telefoneNovo != null && telefoneExistenteNorm != null
                ? telefoneNovo.equals(telefoneExistenteNorm)
                : nomeNovo != null && nomeNovo.equals(TextoUtil.normalizarParaComparacao(nomeExistente));
    }

    /**
     * Auto-inscrição NUNCA contorna, nem para admin. Vaga não entra aqui — é barrada por {@link #validarVaga}, sempre.
     * @return {@code true} se contornou um impedimento deliberadamente ({@link InscricaoEvento#isInscritoPorExcecao}).
     */
    private boolean validarElegibilidade(Evento evento, Pessoa membro, String role, boolean confirmado,
                                          UUID igrejaId) {
        Elegibilidade elegibilidade = elegibilidadeService.avaliar(evento, membro);
        if (elegibilidade.apto()) return false;

        // Quem gerencia pode contornar restrição contornável mesmo na própria inscrição; exige `confirmado` explícito.
        boolean podeGerenciar = Permissoes.podeGerenciarInscricoes(role)
                && evento.getIgreja().getId().equals(igrejaId);
        boolean podeContornar = podeGerenciar
                && confirmado
                && elegibilidade.totalmenteContornavel();

        if (!podeContornar) {
            throw NaoElegivelException.para(elegibilidade.impedimentos(), podeGerenciar);
        }
        return true;
    }

    /** Exposto para {@link com.domus.api.modules.evento.EventoService} recusar reduzir vagas abaixo do confirmado. */
    @Transactional(readOnly = true)
    public long contarPessoasConfirmadas(UUID eventoId) {
        return inscricaoRepository.contarPessoasConfirmadas(eventoId);
    }

    /** {@code vagas == null} significa sem limite. */
    void validarVaga(Evento evento, int pessoasAAdicionar) {
        if (evento.getVagas() == null) return;

        long ocupadas = contarOcupadas(evento);
        if (ocupadas + pessoasAAdicionar > evento.getVagas()) {
            throw new BusinessException("VAGAS_ESGOTADAS",
                    "As vagas deste evento estão esgotadas.");
        }
    }

    /**
     * Evento pago reserva vaga pela cobrança (PAGO ou PENDENTE ainda não expirada), não pela
     * inscrição confirmada — a inscrição é sempre confirmada na hora, pago ou não; quem de
     * fato "segura" a vaga é a cobrança (expira e libera sozinha se ninguém pagar). Evento
     * gratuito continua exatamente como antes: conta inscrições confirmadas + convidados.
     */
    private long contarOcupadas(Evento evento) {
        if (evento.getPreco() != null) {
            return cobrancaEventoRepository.contarPessoasComVagaReservada(evento.getId(), Instant.now());
        }
        return inscricaoRepository.contarPessoasConfirmadas(evento.getId());
    }

    /** Convidado sem cadastro ganha inscrição própria (não acompanhante aninhado) — sem
     *  elegibilidade checada (não existe Pessoa pra avaliar), mas ainda bloqueado em evento
     *  exclusivo pra membros. Usado tanto pelo modal presencial (convidadoPorPessoaId = quem
     *  está logado) quanto pelo convite público (convidadoPorPessoaId = dono do token).
     *  {@code convidadoPor} (quem trouxe) e {@code inscritoPorUsuarioId} (quem apertou o botão)
     *  são coisas diferentes: no modal presencial os dois são a mesma pessoa (o admin); no
     *  convite público só o primeiro existe — quem preencheu o formulário não tem usuário
     *  logado nenhum, por isso {@code inscritoPorUsuarioId} vem {@code null} desse fluxo.
     *  {@code visitanteId} só vem preenchido quando o convidado veio da busca de Visitante
     *  já cadastrado (aba "Visitantes" do modal) — habilita checar duplicidade por id, exata,
     *  em vez de comparar nome/telefone. */
    /** Devolvido por {@link #inscreverConvidado} — {@code cobranca} é nulo em evento
     *  gratuito, ou tem valor em evento pago (mesmo padrão de {@code ResultadoInscricao},
     *  usado por {@link #inscreverInterno}). */
    public record ResultadoConvidado(InscricaoEvento inscricao, CobrancaEvento cobranca) {}

    @Transactional
    public ResultadoConvidado inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
                                               String telefone, String email, UUID convidadoPorPessoaId,
                                               UUID inscritoPorUsuarioId, UUID visitanteId,
                                               boolean gerarLink) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        validarOrganizaInscricao(evento, "Este evento não permite convidados.");
        if (evento.isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        if (evento.getPreco() != null) {
            validarContaPagamentoConectada(igrejaId);
        }
        // E-mail é obrigatório em qualquer evento (2026-08-27, decisão do autor) — não só
        // pra mandar o comprovante de pagamento: se o evento gratuito virar pago depois
        // (ver EventoService.aplicarEventoVirouPago), precisa de um jeito de avisar quem
        // já está inscrito sem cadastro.
        if (email == null || email.isBlank()) {
            throw new BusinessException("EMAIL_OBRIGATORIO",
                    "O e-mail é obrigatório para se inscrever em eventos.");
        }
        validarEventoAberto(evento);
        validarConvidadoTopoNaoDuplicado(eventoId, nome, telefone, visitanteId, inscritoPorUsuarioId);
        validarVaga(evento, 1);

        Pessoa convidadoPor = convidadoPorPessoaId == null ? null
                : membroRepository.findByIdAndIgrejaId(convidadoPorPessoaId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        Visitante visitante = visitanteId == null ? null
                : visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));

        // Evento pago: a inscrição nasce AGUARDANDO_PAGAMENTO, mesmo padrão de
        // inscreverInterno (ver Plano 1) — só confirma quando o webhook aprovar.
        StatusInscricao statusInicial = evento.getPreco() != null
                ? StatusInscricao.AGUARDANDO_PAGAMENTO
                : StatusInscricao.CONFIRMADA;

        InscricaoEvento inscricao = InscricaoEvento.builder()
                .igreja(evento.getIgreja())
                .evento(evento)
                .pessoa(null)
                .nomeConvidado(TextoUtil.capitalizar(nome))
                .telefoneConvidado(telefone)
                .emailConvidado(email)
                .convidadoPor(convidadoPor)
                .visitante(visitante)
                .inscritoPorUsuarioId(inscritoPorUsuarioId)
                .status(statusInicial)
                .build();

        InscricaoEvento salva = inscricaoRepository.save(inscricao);

        // pessoaId nulo = convidado sem cadastro (Plano 4b) — resolvido só por
        // inscricaoId (ver CobrancaController). criadoPorUsuarioId pode ser nulo aqui
        // (auto-registro anônimo via /convite/{token}, ver migration V30).
        CobrancaEvento cobranca = null;
        if (evento.getPreco() != null) {
            cobranca = cobrancaEventoService.criarParaTerceiro(igrejaId, eventoId, salva.getId(),
                    null, evento.getPreco(), inscritoPorUsuarioId, gerarLink);
        }

        log.info("Convidado inscrito. evento_id={}, convidado_por_pessoa_id={}, inscrito_por_usuario_id={}, igreja_id={}",
                eventoId, convidadoPorPessoaId, inscritoPorUsuarioId, igrejaId);
        return new ResultadoConvidado(salva, cobranca);
    }

    /**
     * Cancelamento pelo link "Cancelar inscrição" do e-mail de lembrete de pagamento
     * pendente (2026-08-27) — sem sessão, pela mesma garantia de posse do resto do módulo
     * de cobrança (o {@code id} da {@link CobrancaEvento}, UUIDv4, já é a prova de posse;
     * ver {@code CobrancaController}). Só cancela quem ainda está AGUARDANDO_PAGAMENTO — um
     * link velho (a pessoa já pagou, ou já foi cancelada por outro caminho) não faz nada.
     */
    @Transactional
    public void cancelarPorCobranca(UUID cobrancaId) {
        CobrancaEvento cobranca = cobrancaEventoRepository.findById(cobrancaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        InscricaoEvento inscricao = inscricaoRepository.findById(cobranca.getInscricaoId())
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        if (inscricao.getStatus() != StatusInscricao.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoNegocioException("INSCRICAO_NAO_AGUARDA_PAGAMENTO",
                    "Esta inscrição não está mais aguardando pagamento.");
        }
        validarEventoAberto(inscricao.getEvento());
        cancelarInterno(inscricao);
        log.info("Inscrição cancelada via link do e-mail de lembrete. inscricaoId={}, cobrancaId={}",
                inscricao.getId(), cobrancaId);
    }

    /** Evento EM_ANDAMENTO/ENCERRADO não permite cancelamento — presença é histórico. */
    @Transactional
    public void cancelar(UUID inscricaoId, UUID usuarioId, UUID meuMembroId,
                         String role, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);
        validarEventoAberto(inscricao.getEvento());

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean gestorDaMesmaIgreja = ehGestor && inscricao.getIgreja().getId().equals(igrejaId);
        boolean souEu = inscricao.getPessoa() != null && inscricao.getPessoa().getId().equals(meuMembroId);

        if (!gestorDaMesmaIgreja && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode cancelar a inscrição de outra pessoa. "
                    + "Peça a ela ou a um líder da igreja.");
        }

        cancelarInterno(inscricao);
        log.info("Inscrição cancelada. id={}, por_usuario={}, igreja_id={}",
                inscricaoId, usuarioId, igrejaId);
    }

    /** Reusado pelo cancelamento manual e pela remoção por restrição — as respostas de
     *  campo personalizado não voltam numa reinscrição: a linha de inscrição é reaproveitada
     *  (UNIQUE evento+pessoa), então sem isso a reinscrição herdaria resposta velha e
     *  pareceria "já respondido" sem a pessoa ter respondido nada desta vez. Cancelar o
     *  titular NÃO cancela em cascata quem ele convidou — cada convidado é sua própria
     *  {@code InscricaoEvento} e se cancela independentemente (decisão do usuário, 2026-08-26). */
    private void cancelarInterno(InscricaoEvento inscricao) {
        // Roda ANTES de marcar CANCELADA: se o estorno falhar, o BusinessException aborta a
        // transação inteira e a inscrição continua CONFIRMADA — nunca "cancelada no Domus"
        // com o dinheiro ainda retido no Mercado Pago.
        estornarCobrancasDaInscricao(inscricao);
        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
        respostaCampoPersonalizadoRepository.deleteByInscricaoId(inscricao.getId());
    }

    /**
     * PAGO estorna de verdade no Mercado Pago; PENDENTE só cancela (nunca chegou a ser
     * cobrado).
     *
     * <p><b>Important 7 (revisão final de branch) — fail-fast ANTES de mutar qualquer
     * status:</b> antes desta correção, o loop chamava {@code marcarComoCancelado()} em
     * cobranças PENDENTES e só DEPOIS chegava numa cobrança PAGA cujo estorno falhava —
     * o {@code BusinessException} lançado ali abortava a transação do CANCELAMENTO
     * individual, mas em cancelamento em LOTE ({@code cancelarInscricoesEmEventosAbertosPorPessoa},
     * {@code removerInscritosNaoElegiveis}, {@code cancelarInscricoesEmEventosExclusivos})
     * a exceção é capturada por item do lote — então essas mutações "sujas" (cobrança de
     * convidado já marcada CANCELADO, vaga já liberada) eram persistidas no commit do
     * lote mesmo a inscrição continuando CONFIRMADA. Resultado: vaga liberada sem a
     * inscrição ter sido cancelada de verdade.
     *
     * <p>Correção (abordagem a — fail-fast, mais simples que isolar cada item do lote em
     * {@code REQUIRES_NEW}, e resolve o problema na raiz em vez de só conter o dano):
     * primeiro chama TODAS as chamadas externas de estorno (as únicas que podem falhar)
     * e só DEPOIS que todas tiverem sucesso é que qualquer status é mutado — nem PAGO
     * nem PENDENTE. Se uma falhar, nenhuma cobrança desta inscrição muda de estado.
     */
    private void estornarCobrancasDaInscricao(InscricaoEvento inscricao) {
        java.math.BigDecimal valorReembolsado = estornarCobrancasERetornarValor(inscricao);

        // Só existe reembolso pra avisar quando algo foi de fato cobrado e estornado — cobrança
        // PENDENTE cancelada nunca chegou a debitar ninguém.
        if (valorReembolsado.compareTo(java.math.BigDecimal.ZERO) > 0) {
            enviarEmailCancelamento(inscricao, valorReembolsado);
            registrarEstornoNoFinanceiro(inscricao, valorReembolsado);
        }
    }

    /**
     * Núcleo de {@link #estornarCobrancasDaInscricao}, extraído pra reaproveitar em
     * {@link #aplicarEventoVirouGratuito} — que precisa do MESMO estorno fail-fast (PAGO
     * estorna de verdade, PENDENTE só cancela), mas NÃO do e-mail de "inscrição cancelada"
     * (a inscrição continua confirmada, só o evento é que ficou gratuito) nem do
     * lançamento no financeiro embutido aqui (cada chamador decide se/como avisar e
     * registrar, de acordo com o motivo do estorno).
     *
     * @return valor total estornado nesta inscrição (zero se não havia cobrança PAGA).
     */
    private java.math.BigDecimal estornarCobrancasERetornarValor(InscricaoEvento inscricao) {
        List<CobrancaEvento> cobrancas = cobrancaEventoRepository.findByInscricaoId(inscricao.getId());
        if (cobrancas.isEmpty()) return java.math.BigDecimal.ZERO;

        UUID igrejaId = inscricao.getIgreja().getId();

        // 1ª passada: só chamadas externas (podem falhar), NENHUMA mutação de status ainda.
        // Estorna só o RESTANTE (valor - já estornado antes) — achado ao vivo, 2026-08-27:
        // uma cobrança que já tinha recebido estorno parcial (reajuste de preço pra baixo,
        // ver aplicarMudancaValorPago) fazia o cancelamento tentar estornar o valor CHEIO
        // de novo, e o Mercado Pago recusava por falta de saldo pra devolver.
        for (CobrancaEvento cobranca : cobrancas) {
            if (cobranca.getStatus() != StatusCobranca.PAGO) continue;
            java.math.BigDecimal restante = cobranca.valorRestanteParaEstornar();
            if (restante.signum() == 0) continue; // já foi estornada por completo antes
            try {
                mercadoPagoClient.estornarParcial(igrejaId, cobranca.getMpPaymentId(), restante);
            } catch (Exception e) {
                log.error("Falha ao estornar pagamento no Mercado Pago. cobrancaId={} mpPaymentId={}",
                        cobranca.getId(), cobranca.getMpPaymentId(), e);
                // Marca ANTES de lançar — quem chama isso em lote (aplicarEventoVirouGratuito,
                // cancelamento em massa) captura essa exceção e segue pro próximo item, então
                // sem marcar aqui dentro a pendência nunca ficava visível pra ninguém retentar
                // depois (2026-08-27).
                cobranca.marcarEstornoPendente();
                cobrancaEventoRepository.save(cobranca);
                throw new BusinessException("FALHA_ESTORNO",
                        "FALHA_ESTORNO: não foi possível estornar o pagamento. Tente novamente em instantes.");
            }
        }

        // 2ª passada: todas as chamadas externas tiveram sucesso — agora sim muta status.
        java.math.BigDecimal valorReembolsado = java.math.BigDecimal.ZERO;
        for (CobrancaEvento cobranca : cobrancas) {
            if (cobranca.getStatus() == StatusCobranca.PAGO) {
                java.math.BigDecimal restante = cobranca.valorRestanteParaEstornar();
                if (restante.signum() > 0) {
                    cobranca.registrarEstorno(restante);
                    valorReembolsado = valorReembolsado.add(restante);
                }
            } else if (cobranca.getStatus() == StatusCobranca.PENDENTE) {
                cobranca.marcarComoCancelado();
            }
        }
        cobrancaEventoRepository.saveAll(cobrancas);
        return valorReembolsado;
    }

    /** Espelha a entrada que {@code MercadoPagoWebhookService.registrarNoFinanceiro} criou
     *  quando o pagamento foi confirmado — nunca quebra o cancelamento em si, só loga. */
    private void registrarEstornoNoFinanceiro(InscricaoEvento inscricao, java.math.BigDecimal valorReembolsado) {
        try {
            String nomePagador = inscricao.getPessoa() != null
                ? inscricao.getPessoa().getNome()
                : inscricao.getNomeConvidado();
            movimentacaoAutomaticaService.registrarSaidaDeEvento(
                inscricao.getIgreja().getId(), valorReembolsado,
                "Reembolso — " + inscricao.getEvento().getTitulo() + " (" + nomePagador + ")",
                inscricao.getPessoa() != null ? inscricao.getPessoa().getId() : null, nomePagador);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar estorno na movimentação financeira. inscricaoId={}", inscricao.getId(), e);
        }
    }

    /**
     * Retry de "Estorno pendente" (2026-08-27) — o admin vê a tag na lista de inscritos
     * (ver {@code estorno_pendente} em {@link CobrancaEvento}) e tenta de novo o MESMO
     * estorno que falhou em algum fluxo de estorno em massa (evento virou gratuito, preço
     * baixou, arquivamento de evento, remoção de não-elegível...). Só mexe no dinheiro
     * desta cobrança específica: não tenta adivinhar nem refazer a mudança de status que o
     * fluxo original faria (ex.: cancelar a inscrição de vez) — uma vez que o "restante a
     * estornar" chega a zero, o próprio botão "Cancelar inscrição" (ou uma nova tentativa
     * do fluxo original) resolve o resto sem tentar estornar de novo, porque
     * {@link CobrancaEvento#valorRestanteParaEstornar()} já dá zero.
     */
    @Transactional
    public void tentarEstornoNovamente(UUID cobrancaId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new BusinessException("SEM_PERMISSAO", "Você não pode gerenciar estornos.");
        }
        CobrancaEvento cobranca = cobrancaEventoRepository.findById(cobrancaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        if (!cobranca.getIgrejaId().equals(igrejaId)) {
            throw new ResourceNotFoundException("Cobrança não encontrada.");
        }
        if (!cobranca.isEstornoPendente()) {
            return; // nada pendente — botão pode ter sido clicado duas vezes
        }
        java.math.BigDecimal restante = cobranca.valorRestanteParaEstornar();
        if (restante.signum() == 0) {
            // resolvido por outro caminho enquanto isso — só limpa a tag
            cobranca.registrarEstorno(java.math.BigDecimal.ZERO);
            cobrancaEventoRepository.save(cobranca);
            return;
        }
        try {
            mercadoPagoClient.estornarParcial(igrejaId, cobranca.getMpPaymentId(), restante);
        } catch (Exception e) {
            log.error("Nova tentativa de estorno falhou de novo. cobrancaId={} mpPaymentId={}",
                    cobranca.getId(), cobranca.getMpPaymentId(), e);
            throw new BusinessException("FALHA_ESTORNO",
                    "Não foi possível estornar. Tente novamente mais tarde.");
        }
        cobranca.registrarEstorno(restante);
        cobrancaEventoRepository.save(cobranca);

        InscricaoEvento inscricao = inscricaoRepository.findById(cobranca.getInscricaoId()).orElse(null);
        if (inscricao != null) {
            registrarEstornoNoFinanceiro(inscricao, restante);
        }
        log.info("Estorno pendente resolvido manualmente. cobrancaId={}, valor={}", cobrancaId, restante);
    }

    /**
     * Aviso de cancelamento com reembolso — mesmo padrão de resolução de destinatário e
     * mesma postura de falha (nunca quebra o cancelamento em si) do e-mail de confirmação
     * de pagamento em {@code MercadoPagoWebhookService.enviarEmailConfirmacao}.
     */
    private void enviarEmailCancelamento(InscricaoEvento inscricao, java.math.BigDecimal valorReembolsado) {
        String nomeDestinatario;
        String email;

        if (inscricao.getPessoa() != null) {
            nomeDestinatario = inscricao.getPessoa().getNome();
            email = inscricao.getPessoa().getEmail();
        } else {
            // Convidado sem cadastro em evento pago sempre tem e-mail (obrigatório desde a
            // feature de comprovante por e-mail); em evento gratuito pode não ter.
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }

        if (email == null || email.isBlank()) {
            log.info("Inscrição cancelada com reembolso, sem e-mail pra avisar. inscricaoId={}", inscricao.getId());
            return;
        }

        Evento evento = inscricao.getEvento();
        String valorFormatado = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"))
                .format(valorReembolsado);

        String corpo = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2 style="text-align: center; color: #131b2e;">Inscrição cancelada</h2>
                  <p>Olá, %s.</p>
                  <p>Sua inscrição no evento abaixo foi cancelada.</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0; font-weight: bold; color: #131b2e;">%s</p>
                  </div>
                  <p style="color: #64748b; font-size: 14px;">
                    O valor de <strong>%s</strong> será reembolsado de acordo com o método de pagamento
                    que você utilizou. O prazo para o reembolso aparecer depende do seu banco ou
                    operadora do cartão — costuma ser em poucos dias, mas pode levar até duas faturas
                    em alguns casos.
                  </p>
                </div>
                """.formatted(nomeDestinatario, evento.getTitulo(), valorFormatado);

        try {
            emailService.enviar(email, "Inscrição cancelada — " + evento.getTitulo(), corpo);
            log.info("E-mail de cancelamento com reembolso enviado. inscricaoId={}", inscricao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de cancelamento com reembolso. inscricaoId={}", inscricao.getId(), e);
        }
    }

    /**
     * Chamado ao arquivar a pessoa (antes do soft delete, pessoa ainda ativa aqui). Evento que
     * ainda vai acontecer perde a vaga dela — cancela, como qualquer cancelamento normal. Evento
     * já em andamento ou encerrado não mexe: ela participou, isso é histórico e continua exibindo
     * os dados normalmente (ela só está arquivada, não excluída).
     */
    @Transactional
    public void cancelarInscricoesEmEventosAbertosPorPessoa(UUID pessoaId) {
        List<InscricaoEvento> confirmadas = inscricaoRepository.findByPessoaIdAndStatus(pessoaId, StatusInscricao.CONFIRMADA);
        for (InscricaoEvento inscricao : confirmadas) {
            if (inscricao.getEvento().getSituacao() == SituacaoEvento.AGENDADO) {
                try {
                    cancelarInterno(inscricao);
                } catch (BusinessException e) {
                    if (!"FALHA_ESTORNO".equals(e.getCodigo())) throw e;
                    log.error("Falha ao estornar cobrança da inscrição {} durante cancelamento em lote "
                            + "(pessoa arquivada) — pessoa mantida, requer retry manual", inscricao.getId(), e);
                }
            }
        }
    }

    /**
     * Chamado por {@code EventoService.arquivarEvento} (2026-08-27) — arquivar um evento
     * pago com gente já confirmada/paga precisa devolver o dinheiro de quem pagou, do
     * mesmo jeito que virar gratuito faz; sem isto, arquivar um evento pago simplesmente
     * sumia com a cobrança sem avisar nem devolver nada. Mesmo padrão fail-fast por item
     * dos outros cancelamentos em lote — uma falha de estorno não trava o arquivamento
     * inteiro, só marca a cobrança como "estorno pendente" pra retry manual depois.
     *
     * @return quantas inscrições foram canceladas com sucesso.
     */
    @Transactional
    public int cancelarTodasInscricoesDoEventoComEstorno(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA
                        || i.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO)
                .toList();
        int canceladas = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            try {
                cancelarInterno(inscricao);
                canceladas++;
            } catch (BusinessException e) {
                if (!"FALHA_ESTORNO".equals(e.getCodigo())) throw e;
                log.error("Falha ao estornar cobrança da inscrição {} durante arquivamento do evento "
                        + "— pessoa mantida como estava, requer retry manual", inscricao.getId(), e);
            }
        }
        if (canceladas > 0) {
            log.info("Inscrições canceladas por arquivamento do evento. evento_id={}, canceladas={}",
                    eventoId, canceladas);
        }
        // Flush explícito: EventoService.arquivarEvento chama eventoRepository.delete(evento)
        // logo em seguida, na MESMA transação — sem isto, as InscricaoEvento cujo status mudou
        // aqui ainda ficam pendentes de flush quando o Evento (que elas referenciam) é
        // removido da sessão, e o autoflush seguinte lançava TransientObjectException
        // ("unsaved transient instance of Evento") — regressão do MESMO bug que
        // EventoArquivamentoNotificaInscritosTest já cobria pra notificarInscritos
        // (achado ao vivo, 2026-08-27).
        inscricaoRepository.flush();
        return canceladas;
    }

    /** Só roda com escolha explícita do admin ({@code cancelarNaoElegiveis}); pula exceções deliberadas. */
    @Transactional
    public int removerInscritosNaoElegiveis(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        int removidos = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            Pessoa pessoa = resolverPessoa(inscricao, pessoas);
            // Pessoa excluída (LGPD): não dá pra reavaliar elegibilidade de quem não existe mais.
            if (inscricao.isInscritoPorExcecao() || pessoa == null) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(inscricao.getEvento(), pessoa);
            if (!elegibilidade.apto()) {
                try {
                    cancelarInterno(inscricao);
                    removidos++;
                } catch (BusinessException e) {
                    if (!"FALHA_ESTORNO".equals(e.getCodigo())) throw e;
                    log.error("Falha ao estornar cobrança da inscrição {} durante remoção em lote "
                            + "de não-elegíveis — pessoa mantida, requer retry manual", inscricao.getId(), e);
                }
            }
        }

        if (removidos > 0) {
            log.info("Inscrições removidas por restrição de evento (escolha explícita). "
                    + "evento_id={}, removidos={}", eventoId, removidos);
        }
        return removidos;
    }

    /** Prévia pura (nada gravado); pula exceção deliberada — o admin já escolheu manter aquela inscrição. */
    @Transactional(readOnly = true)
    public List<ImpactoRestricaoResponse.InscritoImpactado> calcularImpacto(
            UUID eventoId, Evento regrasHipoteticas) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        List<ImpactoRestricaoResponse.InscritoImpactado> afetados = new ArrayList<>();

        for (InscricaoEvento inscricao : inscricoes) {
            Pessoa pessoa = resolverPessoa(inscricao, pessoas);
            if (inscricao.isInscritoPorExcecao() || pessoa == null) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(regrasHipoteticas, pessoa);
            if (!elegibilidade.apto()) {
                List<String> motivos = elegibilidade.impedimentos().stream()
                        .map(Impedimento::mensagem)
                        .toList();
                afetados.add(new ImpactoRestricaoResponse.InscritoImpactado(
                        pessoa.getId(), pessoa.getNome(), motivos));
            }
        }
        return afetados;
    }

    /**
     * Chamada quando o vínculo deixa de ser MEMBRO — sem isto a pessoa continuaria ocupando vaga em evento exclusivo.
     * @return quantas inscrições foram canceladas.
     */
    @Transactional
    public int cancelarInscricoesEmEventosExclusivos(UUID pessoaId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository
                .findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(pessoaId, StatusInscricao.CONFIRMADA);

        int canceladas = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            try {
                cancelarInterno(inscricao);
                canceladas++;
            } catch (BusinessException e) {
                if (!"FALHA_ESTORNO".equals(e.getCodigo())) throw e;
                log.error("Falha ao estornar cobrança da inscrição {} durante cancelamento em lote "
                        + "(perda de vínculo MEMBRO) — pessoa mantida, requer retry manual", inscricao.getId(), e);
            }
        }

        if (canceladas > 0) {
            log.info("Inscrições canceladas por perda de vínculo MEMBRO. pessoa_id={}, canceladas={}",
                    pessoaId, canceladas);
        }
        return canceladas;
    }

    /** {@code busca} e a paginação afetam só {@code inscritos} — total/vagas restantes contam TODAS as confirmadas. */
    @Transactional(readOnly = true)
    public ListaInscritosResponse listarInscritos(UUID eventoId, UUID igrejaId, String busca, Pageable pageable) {
        // IncluindoArquivados: a tela de Arquivados também abre a lista de inscritos de um
        // evento arquivado (pra só olhar) — arquivar não desvincula ninguém.
        Evento evento = eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        // JOIN FETCH de coleção paginaria em memória no Hibernate — ids paginados primeiro, detalhes por IN depois.
        Page<UUID> idsPagina = inscricaoRepository.listarIdsPaginadoPorEvento(eventoId, busca, pageable);
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarComDetalhesPorIds(idsPagina.getContent());

        // Resolve "quem inscreveu" em UMA query (evita N+1); id ausente no mapa (conta arquivada) vira null explícito.
        Map<UUID, RegistranteResumo> registrantes = buscarRegistrantesEmLote(inscricoes);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        // Quem já pagou algo (diferencia a tag "Pagamento pendente" de "Falta
        // complementar" — ver InscritoResponse.pagamentoParcial).
        List<UUID> idsDaPagina = inscricoes.stream().map(InscricaoEvento::getId).toList();
        java.util.Set<UUID> comCobrancaPaga = new java.util.HashSet<>(
                cobrancaEventoRepository.findInscricaoIdsComCobrancaPaga(idsDaPagina));
        // Tag "Estorno pendente" (2026-08-27) — mapeia inscrição -> id da cobrança pendente
        // de retry (nunca mais de uma cobrança com estorno pendente por inscrição na prática).
        Map<UUID, UUID> comEstornoPendente = cobrancaEventoRepository
                .findByInscricaoIdInAndEstornoPendenteTrue(idsDaPagina).stream()
                .collect(java.util.stream.Collectors.toMap(CobrancaEvento::getInscricaoId, CobrancaEvento::getId,
                        (a, b) -> a));

        List<InscritoResponse> inscritosDaPagina = inscricoes.stream()
                .map(i -> InscritoResponse.from(i,
                        resolverPessoa(i, pessoas),
                        registrantes.get(i.getInscritoPorUsuarioId()),
                        resolverConvidadoPor(i, pessoas),
                        comCobrancaPaga.contains(i.getId()),
                        comEstornoPendente.get(i.getId())))
                .toList();
        PagedResponse<InscritoResponse> paginaInscritos = PagedResponse.from(
                new PageImpl<>(inscritosDaPagina, pageable, idsPagina.getTotalElements()));

        long total = inscricaoRepository.contarPessoasConfirmadas(eventoId);
        Integer restantes = evento.getVagas() == null
                ? null
                : Math.max(0, evento.getVagas() - (int) total);

        return new ListaInscritosResponse(total, evento.getVagas(), restantes, paginaInscritos);
    }

    /** Visível a QUALQUER MEMBRO — versão reduzida de {@link #listarInscritos} (ver {@link ParticipanteResponse}). */
    @Transactional(readOnly = true)
    public List<ParticipanteResponse> listarParticipantes(UUID eventoId, UUID igrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        // IncluindoArquivados: mesma razão do buscarPorId — abrir o detalhe de um evento
        // arquivado (tela de Arquivados) também carrega essa lista reduzida.
        eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .or(() -> eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        Map<UUID, Pessoa> pessoas = resolverPessoasEmLote(inscricoes);
        return inscricoes.stream()
                .map(i -> ParticipanteResponse.from(i, resolverPessoa(i, pessoas), resolverConvidadoPor(i, pessoas)))
                .toList();
    }

    /**
     * Em lote, via bypass do @SQLRestriction (nunca {@code i.getPessoa().getNome()} direto) —
     * pessoa arquivada (mas não excluída) continua tendo os dados reais resolvidos aqui; some
     * do mapa só quando foi excluída de vez (pessoa_id já é NULL nesse caso, nem entra na busca).
     */
    private Map<UUID, Pessoa> resolverPessoasEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = new ArrayList<>();
        for (InscricaoEvento i : inscricoes) {
            if (i.getPessoa() != null) ids.add(i.getPessoa().getId());
            if (i.getConvidadoPor() != null) ids.add(i.getConvidadoPor().getId());
        }
        List<UUID> idsUnicos = ids.stream().distinct().toList();
        if (idsUnicos.isEmpty()) {
            return Map.of();
        }
        return membroRepository.findByIdInIncluindoArquivadas(idsUnicos).stream()
                .collect(java.util.stream.Collectors.toMap(Pessoa::getId, p -> p));
    }

    /**
     * Resolve a pessoa de exibição pra uma inscrição. Em produção, {@code i.getPessoa()} é sempre
     * um proxy lazy do Hibernate (repositório nunca mais faz JOIN FETCH em pessoa) — usa o mapa
     * resolvido em lote pra não estourar @SQLRestriction se ela estiver arquivada. Em teste
     * (objeto construído direto via builder), não é proxy — usa direto, sem depender do mock do
     * bypass estar stubado.
     */
    private Pessoa resolverPessoa(InscricaoEvento i, Map<UUID, Pessoa> pessoasResolvidas) {
        Pessoa p = i.getPessoa();
        if (p == null) return null;
        if (!(p instanceof org.hibernate.proxy.HibernateProxy)) return p;
        return pessoasResolvidas.get(p.getId());
    }

    private Pessoa resolverConvidadoPor(InscricaoEvento i, Map<UUID, Pessoa> pessoasResolvidas) {
        Pessoa p = i.getConvidadoPor();
        if (p == null) return null;
        if (!(p instanceof org.hibernate.proxy.HibernateProxy)) return p;
        return pessoasResolvidas.get(p.getId());
    }

    /** Numa única query (ou nenhuma, se ninguém foi inscrito por terceiro). */
    private Map<UUID, RegistranteResumo> buscarRegistrantesEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = inscricoes.stream()
                .map(InscricaoEvento::getInscritoPorUsuarioId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        // HashMap, não Map.of(): .get(null) é consulta legítima aqui (auto-inscrição) e Map.of() lança NPE em chave nula.
        Map<UUID, RegistranteResumo> mapa = new HashMap<>();
        if (ids.isEmpty()) {
            return mapa;
        }

        for (RegistranteResumo r : usuarioRepository.buscarRegistrantes(ids)) {
            mapa.put(r.usuarioId(), r);
        }
        return mapa;
    }

    private InscricaoEvento buscarInscricao(UUID id, UUID minhaIgrejaId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(minhaIgrejaId);
        return inscricaoRepository.buscarVisivelParaFamilia(id, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
    }

    /**
     * Fluxo real é "quase todo mundo veio" — exceções se corrigem depois via {@link #marcarPresencaInscricao}.
     * @return quantas inscrições (cada convidado já é a sua própria) foram marcadas.
     */
    @Transactional
    public int marcarTodosPresentes(UUID eventoId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarControlaPresenca(evento);

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int marcados = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            inscricao.setCompareceu(true);
            marcados++;
            inscricaoRepository.save(inscricao);
        }

        log.info("Presença marcada em lote. evento_id={}, pessoas_marcadas={}, igreja_id={}",
                eventoId, marcados, igrejaId);
        return marcados;
    }

    /** Reverte um "marcar todos" feito sem querer, ou reinicia a contagem de presença do zero. */
    @Transactional
    public int desmarcarTodosPresentes(UUID eventoId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarControlaPresenca(evento);

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int desmarcados = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            inscricao.setCompareceu(false);
            desmarcados++;
            inscricaoRepository.save(inscricao);
        }

        log.info("Presença desmarcada em lote. evento_id={}, pessoas_desmarcadas={}, igreja_id={}",
                eventoId, desmarcados, igrejaId);
        return desmarcados;
    }

    /** Corrige a exceção de um inscrito específico após o "marcar todos" (ou o contrário). */
    @Transactional
    public void marcarPresencaInscricao(UUID eventoId, UUID inscricaoId, boolean compareceu,
                                        UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        InscricaoEvento inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        validarControlaPresenca(inscricao.getEvento());
        validarInscricaoConfirmada(inscricao);

        inscricao.setCompareceu(compareceu);
        inscricaoRepository.save(inscricao);
        log.info("Presença individual marcada. inscricao_id={}, compareceu={}, igreja_id={}",
                inscricaoId, compareceu, igrejaId);
    }

    /** Espelha o CHECK do banco (V6): sem controlaPresenca não existe presença para marcar. */
    private void validarControlaPresenca(Evento evento) {
        if (!evento.isControlaPresenca()) {
            throw new ConflitoNegocioException("PRESENCA_NAO_HABILITADA",
                    "Este evento não controla presença.");
        }
    }

    /** Inscrição CANCELADA não deveria ter presença marcada/desmarcada. */
    private void validarInscricaoConfirmada(InscricaoEvento inscricao) {
        if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) {
            throw new ConflitoNegocioException("INSCRICAO_NAO_CONFIRMADA",
                    "Esta inscrição está cancelada e não pode ter presença marcada.");
        }
    }

    /**
     * Chamado por {@code EventoService.atualizarEvento} quando um evento pago vira
     * gratuito (preço deixa de ser nulo) — decisão do usuário (2026-08-27): ninguém perde a
     * vaga, cada um é tratado assim:
     * <ul>
     *   <li>Cobrança PAGA → estorna de verdade no Mercado Pago (mesmo mecanismo fail-fast
     *       de {@link #estornarCobrancasERetornarValor}), lança saída no financeiro, avisa
     *       por e-mail.</li>
     *   <li>Cobrança PENDENTE → cancela (não vai mais cobrar ninguém).</li>
     *   <li>Inscrição {@code AGUARDANDO_PAGAMENTO} → vira {@code CONFIRMADA} (o evento
     *       agora é de graça, não faz sentido continuar esperando pagamento).</li>
     * </ul>
     * Falha pontual de estorno (Mercado Pago fora do ar) não trava o lote inteiro — mesmo
     * padrão de {@code removerInscritosNaoElegiveis}: loga pra retry manual, segue pras
     * outras inscrições.
     *
     * @return quantas inscrições foram processadas com sucesso.
     */
    /**
     * Prévia pura (nada gravado, nenhuma chamada ao Mercado Pago) de
     * {@link #aplicarEventoVirouGratuito} — o front chama isso pra mostrar "isso vai
     * estornar R$X de N pessoas" antes do admin confirmar de verdade a mudança de preço.
     */
    @Transactional(readOnly = true)
    public com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse calcularImpactoEventoVirarGratuito(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA
                        || i.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO)
                .toList();

        int pessoasComPagamentoPago = 0;
        java.math.BigDecimal valorTotalAEstornar = java.math.BigDecimal.ZERO;
        int pessoasAguardandoPagamento = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            if (inscricao.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO) {
                pessoasAguardandoPagamento++;
            }
            for (CobrancaEvento cobranca : cobrancaEventoRepository.findByInscricaoId(inscricao.getId())) {
                if (cobranca.getStatus() == StatusCobranca.PAGO) {
                    pessoasComPagamentoPago++;
                    valorTotalAEstornar = valorTotalAEstornar.add(cobranca.getValor());
                }
            }
        }

        if (pessoasComPagamentoPago == 0 && pessoasAguardandoPagamento == 0) {
            return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.semImpacto();
        }
        return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.pagoParaGratuito(
                pessoasComPagamentoPago, valorTotalAEstornar, pessoasAguardandoPagamento);
    }

    /**
     * Prévia pura (nada gravado) de {@link #aplicarEventoVirouPago} — quantas pessoas já
     * confirmadas ganhariam uma cobrança nova e quanto isso somaria no total.
     */
    @Transactional(readOnly = true)
    public com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse calcularImpactoEventoVirarPago(
            UUID eventoId, java.math.BigDecimal precoNovo) {
        int pessoasSeraoCobradas = (int) inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA)
                .count();

        if (pessoasSeraoCobradas == 0) {
            return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.semImpacto();
        }
        return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.gratuitoParaPago(
                pessoasSeraoCobradas, precoNovo.multiply(java.math.BigDecimal.valueOf(pessoasSeraoCobradas)));
    }

    @Transactional
    public int aplicarEventoVirouGratuito(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA
                        || i.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO)
                .toList();

        int processadas = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            boolean estavaAguardandoPagamento = inscricao.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO;
            java.math.BigDecimal valorReembolsado;
            try {
                valorReembolsado = estornarCobrancasERetornarValor(inscricao);
            } catch (BusinessException e) {
                if (!"FALHA_ESTORNO".equals(e.getCodigo())) throw e;
                log.error("Falha ao estornar cobrança da inscrição {} durante conversão do evento "
                        + "pra gratuito — inscrição mantida como estava, requer retry manual",
                        inscricao.getId(), e);
                continue;
            }

            if (estavaAguardandoPagamento) {
                inscricao.setStatus(StatusInscricao.CONFIRMADA);
                inscricaoRepository.save(inscricao);
            }

            if (valorReembolsado.compareTo(java.math.BigDecimal.ZERO) > 0) {
                registrarEstornoNoFinanceiro(inscricao, valorReembolsado);
                enviarEmailEventoVirouGratuito(inscricao, valorReembolsado);
            } else if (estavaAguardandoPagamento) {
                // Não chegou a pagar (cobrança PENDENTE só cancelada, sem estorno) — mesmo
                // assim é notícia boa o bastante pra avisar: não precisa mais pagar nada.
                enviarEmailEventoVirouGratuito(inscricao, java.math.BigDecimal.ZERO);
            }
            processadas++;
        }

        if (processadas > 0) {
            log.info("Evento virou gratuito, inscrições processadas. evento_id={}, processadas={}",
                    eventoId, processadas);
        }
        return processadas;
    }

    /**
     * @param valorReembolsado zero quando a pessoa nunca chegou a pagar (cobrança PENDENTE
     *                         só cancelada) — o texto do e-mail muda de acordo.
     */
    private void enviarEmailEventoVirouGratuito(InscricaoEvento inscricao, java.math.BigDecimal valorReembolsado) {
        String nomeDestinatario;
        String email;

        if (inscricao.getPessoa() != null) {
            nomeDestinatario = inscricao.getPessoa().getNome();
            email = inscricao.getPessoa().getEmail();
        } else {
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }

        if (email == null || email.isBlank()) {
            log.info("Evento virou gratuito, sem e-mail pra avisar. inscricaoId={}", inscricao.getId());
            return;
        }

        Evento evento = inscricao.getEvento();
        boolean houveReembolso = valorReembolsado.compareTo(java.math.BigDecimal.ZERO) > 0;
        String paragrafoValor = houveReembolso
                ? """
                  <p style="color: #64748b; font-size: 14px;">
                    O valor de <strong>%s</strong> que você pagou será reembolsado de acordo com o
                    método de pagamento que você utilizou. O prazo para o reembolso aparecer depende
                    do seu banco ou operadora do cartão — costuma ser em poucos dias, mas pode levar
                    até duas faturas em alguns casos.
                  </p>
                  """.formatted(java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"))
                        .format(valorReembolsado))
                : "<p style=\"color: #64748b; font-size: 14px;\">Você não precisa mais pagar nada — sua inscrição já está confirmada.</p>";

        String corpo = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2 style="text-align: center; color: #131b2e;">O evento passou a ser gratuito</h2>
                  <p>Olá, %s.</p>
                  <p>O evento abaixo, em que você está inscrito(a), deixou de ser pago.</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0; font-weight: bold; color: #131b2e;">%s</p>
                  </div>
                  <p>Sua inscrição continua confirmada, sem nenhuma mudança além do valor.</p>
                  %s
                </div>
                """.formatted(nomeDestinatario, evento.getTitulo(), paragrafoValor);

        try {
            emailService.enviar(email, "Evento passou a ser gratuito — " + evento.getTitulo(), corpo);
            log.info("E-mail de evento-virou-gratuito enviado. inscricaoId={}", inscricao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de evento-virou-gratuito. inscricaoId={}", inscricao.getId(), e);
        }
        notificarPessoaSeForUsuario(inscricao, com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_VIROU_GRATUITO,
                "\"" + evento.getTitulo() + "\" passou a ser gratuito." + (houveReembolso ? " O valor pago será reembolsado." : ""));
    }

    /**
     * Chamado por {@code EventoService.atualizarEvento} quando um evento gratuito vira
     * pago (preço deixa de ser nulo) — decisão do usuário (2026-08-27): ninguém perde a
     * vaga; cada inscrição CONFIRMADA ganha uma cobrança nova (PENDENTE, com link — mesmo
     * modo "gerarLink" usado pra convidar terceiro a pagar, já que ninguém está numa tela
     * de checkout agora) e vira AGUARDANDO_PAGAMENTO até a pessoa pagar. Igreja sem conta
     * de pagamento conectada barra a edição inteira (mesma checagem de {@code inscrever}).
     *
     * @return quantas inscrições foram processadas.
     */
    @Transactional
    public int aplicarEventoVirouPago(UUID eventoId, java.math.BigDecimal precoNovo, UUID usuarioId) {
        List<InscricaoEvento> confirmadas = inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA)
                .toList();
        if (confirmadas.isEmpty()) return 0;

        validarContaPagamentoConectada(confirmadas.get(0).getIgreja().getId());

        int processadas = 0;
        for (InscricaoEvento inscricao : confirmadas) {
            UUID pessoaId = inscricao.getPessoa() != null ? inscricao.getPessoa().getId() : null;
            CobrancaEvento cobranca = cobrancaEventoService.criarParaTerceiro(
                    inscricao.getIgreja().getId(), eventoId, inscricao.getId(), pessoaId, precoNovo, usuarioId, true);
            inscricao.setStatus(StatusInscricao.AGUARDANDO_PAGAMENTO);
            inscricaoRepository.save(inscricao);
            enviarEmailEventoVirouPago(inscricao, cobranca, precoNovo);
            processadas++;
        }

        log.info("Evento virou pago, inscrições processadas. evento_id={}, processadas={}", eventoId, processadas);
        return processadas;
    }

    private void enviarEmailEventoVirouPago(InscricaoEvento inscricao, CobrancaEvento cobranca, java.math.BigDecimal preco) {
        String nomeDestinatario;
        String email;

        if (inscricao.getPessoa() != null) {
            nomeDestinatario = inscricao.getPessoa().getNome();
            email = inscricao.getPessoa().getEmail();
        } else {
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }

        if (email == null || email.isBlank()) {
            log.info("Evento virou pago, sem e-mail pra avisar. inscricaoId={}", inscricao.getId());
            return;
        }

        Evento evento = inscricao.getEvento();
        String valorFormatado = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"))
                .format(preco);
        String link = frontendUrl + "/cobranca/" + cobranca.getTokenLinkPublico();

        String corpo = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2 style="text-align: center; color: #131b2e;">O evento passou a ser pago</h2>
                  <p>Olá, %s.</p>
                  <p>O evento abaixo, em que você está inscrito(a), passou a cobrar inscrição.</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0; font-weight: bold; color: #131b2e;">%s</p>
                  </div>
                  <p>
                    Sua inscrição continua garantida, mas precisa ser paga —
                    <strong>%s</strong> — pra ficar confirmada de vez. Pague pelo link abaixo:
                  </p>
                  <p style="text-align: center; margin: 24px 0;">
                    <a href="%s" style="display: inline-block; background: #131b2e; color: #fff; padding: 12px 24px; border-radius: 8px; text-decoration: none;">Pagar inscrição</a>
                  </p>
                </div>
                """.formatted(nomeDestinatario, evento.getTitulo(), valorFormatado, link);

        try {
            emailService.enviar(email, "Evento passou a ser pago — " + evento.getTitulo(), corpo);
            log.info("E-mail de evento-virou-pago enviado. inscricaoId={}", inscricao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de evento-virou-pago. inscricaoId={}", inscricao.getId(), e);
        }
        notificarPessoaSeForUsuario(inscricao, com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_VIROU_PAGO,
                "\"" + evento.getTitulo() + "\" passou a ser pago — falta pagar " + valorFormatado + " pra confirmar sua vaga.");
    }

    /** Notifica dentro do próprio Domus, além do e-mail — só quando a pessoa tem conta
     *  (usuario), mesmo padrão de {@code CampoPersonalizadoService.notificarInscritosSobrePendencia}. */
    private void notificarPessoaSeForUsuario(InscricaoEvento inscricao, com.domus.api.modules.notificacao.TipoNotificacao tipo, String texto) {
        if (inscricao.getPessoa() == null) return; // convidado sem cadastro não tem conta pra notificar
        usuarioRepository.findByPessoaId(inscricao.getPessoa().getId())
                .ifPresent(usuario -> notificacaoService.criar(
                        tipo, inscricao.getIgreja().getId(), usuario.getId(), texto,
                        "/eventos?detalhe=" + inscricao.getEvento().getId()));
    }

    /**
     * Prévia pura (nada gravado, nenhuma chamada ao Mercado Pago) de
     * {@link #aplicarMudancaValorPago} — evento continua pago, só o valor mudou.
     */
    @Transactional(readOnly = true)
    public com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse calcularImpactoMudancaValorPago(
            UUID eventoId, java.math.BigDecimal precoAntigo, java.math.BigDecimal precoNovo) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA
                        || i.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO)
                .toList();
        if (inscricoes.isEmpty()) {
            return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.semImpacto();
        }
        Map<UUID, List<CobrancaEvento>> cobrancasPorInscricao = cobrancaEventoRepository.findByEventoId(eventoId).stream()
                .collect(java.util.stream.Collectors.groupingBy(CobrancaEvento::getInscricaoId));

        int pessoasComDiferencaAPagar = 0;
        java.math.BigDecimal valorTotalACobrar = java.math.BigDecimal.ZERO;
        int pessoasComDiferencaAEstornar = 0;
        java.math.BigDecimal valorTotalAEstornar = java.math.BigDecimal.ZERO;
        int pessoasNuncaPagaram = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            // valorJaPago (não o status) é quem realmente decide o quanto falta — status
            // sozinho não distingue "nunca pagou nada" de "já pagou e está esperando pagar
            // só um complemento de um reajuste anterior" (achado ao vivo, 2026-08-27: essa
            // confusão fazia a prévia de estorno não contar quem tinha um complemento
            // pendente, e fazia a cobrança nova ser calculada com o preço antigo do EVENTO
            // em vez de com o que a pessoa realmente já pagou).
            java.math.BigDecimal valorJaPago = valorJaPago(cobrancasPorInscricao.getOrDefault(inscricao.getId(), List.of()));
            if (valorJaPago.compareTo(java.math.BigDecimal.ZERO) == 0) {
                pessoasNuncaPagaram++;
                continue;
            }
            java.math.BigDecimal novoValorDevido = precoNovo.subtract(valorJaPago);
            if (novoValorDevido.compareTo(java.math.BigDecimal.ZERO) > 0) {
                pessoasComDiferencaAPagar++;
                valorTotalACobrar = valorTotalACobrar.add(novoValorDevido);
            } else if (novoValorDevido.compareTo(java.math.BigDecimal.ZERO) < 0) {
                pessoasComDiferencaAEstornar++;
                valorTotalAEstornar = valorTotalAEstornar.add(novoValorDevido.abs());
            }
        }

        if (pessoasComDiferencaAPagar == 0 && pessoasComDiferencaAEstornar == 0 && pessoasNuncaPagaram == 0) {
            return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.semImpacto();
        }
        // Achado ao vivo (2026-08-27): as duas direções podem coexistir quando o evento já
        // teve reajustes diferentes pra pessoas diferentes antes — a prévia antiga só
        // mostrava a direção "principal" (pela variação do preço do EVENTO) e escondia
        // completamente quem estava na direção oposta, mesmo o backend já calculando os
        // dois valores certinho por trás.
        if (pessoasComDiferencaAPagar > 0 && pessoasComDiferencaAEstornar > 0) {
            return com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.valorMisto(
                    pessoasComDiferencaAEstornar, valorTotalAEstornar,
                    pessoasComDiferencaAPagar, valorTotalACobrar, pessoasNuncaPagaram);
        }
        return precoNovo.compareTo(precoAntigo) > 0
                ? com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.valorAumentou(
                        pessoasComDiferencaAPagar, valorTotalACobrar, pessoasNuncaPagaram)
                : com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.valorDiminuiu(
                        pessoasComDiferencaAEstornar, valorTotalAEstornar, pessoasNuncaPagaram);
    }

    /** Quanto a pessoa REALMENTE ainda tem retido (pago menos o que já foi estornado dela) —
     *  nunca o valor bruto pago, porque um estorno parcial anterior (reajuste de preço pra
     *  baixo) reduz o quanto ela efetivamente "tem pago" pra fins de um próximo reajuste. */
    private java.math.BigDecimal valorJaPago(List<CobrancaEvento> cobrancasDaInscricao) {
        return cobrancasDaInscricao.stream()
                .filter(c -> c.getStatus() == StatusCobranca.PAGO)
                .map(CobrancaEvento::valorRestanteParaEstornar)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    /**
     * Chamado por {@code EventoService.atualizarEvento} quando um evento continua pago mas
     * o valor muda — decisão do usuário (2026-08-27): ninguém perde a vaga. A diferença é
     * calculada por PESSOA (quanto ela já pagou de verdade, somando todas as cobranças
     * PAGO dela — inclusive complementos de reajustes anteriores), nunca pelo preço antigo
     * do evento sozinho: duas pessoas confirmadas no mesmo evento podem ter pago valores
     * diferentes se já passaram por outro reajuste antes (achado ao vivo, 2026-08-27 — um
     * segundo reajuste cobrava o valor cheio de novo em vez de só a diferença restante).
     *
     * <p>Quem já tem uma cobrança PENDENTE em aberto (aguardando um complemento anterior)
     * tem ELA reaproveitada/atualizada — nunca cria uma segunda cobrança de complemento
     * pra mesma inscrição. Quem nunca pagou nada (aguardando o valor cheio desde o início,
     * como em {@link #aplicarEventoVirouPago}) só tem esse valor cheio atualizado.</p>
     *
     * @return quantas inscrições foram processadas.
     */
    @Transactional
    public int aplicarMudancaValorPago(UUID eventoId, java.math.BigDecimal precoAntigo,
                                        java.math.BigDecimal precoNovo, UUID usuarioId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.findByEventoId(eventoId).stream()
                .filter(i -> i.getStatus() == StatusInscricao.CONFIRMADA
                        || i.getStatus() == StatusInscricao.AGUARDANDO_PAGAMENTO)
                .toList();
        if (inscricoes.isEmpty()) return 0;

        Map<UUID, List<CobrancaEvento>> cobrancasPorInscricao = cobrancaEventoRepository.findByEventoId(eventoId).stream()
                .collect(java.util.stream.Collectors.groupingBy(CobrancaEvento::getInscricaoId));

        boolean vaiPrecisarDeContaConectada = false;
        for (InscricaoEvento inscricao : inscricoes) {
            List<CobrancaEvento> cobrancas = cobrancasPorInscricao.getOrDefault(inscricao.getId(), List.of());
            boolean temPendenteAberta = cobrancas.stream().anyMatch(c -> c.getStatus() == StatusCobranca.PENDENTE);
            java.math.BigDecimal valorJaPago = valorJaPago(cobrancas);
            boolean precisaCriarCobrancaNova = valorJaPago.compareTo(java.math.BigDecimal.ZERO) > 0 && !temPendenteAberta
                    && precoNovo.compareTo(valorJaPago) > 0;
            if (precisaCriarCobrancaNova) { vaiPrecisarDeContaConectada = true; break; }
        }
        if (vaiPrecisarDeContaConectada) {
            validarContaPagamentoConectada(inscricoes.get(0).getIgreja().getId());
        }

        int processadas = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            List<CobrancaEvento> cobrancas = cobrancasPorInscricao.getOrDefault(inscricao.getId(), List.of());
            java.math.BigDecimal valorJaPago = valorJaPago(cobrancas);
            var cobrancaPendenteOpt = cobrancas.stream().filter(c -> c.getStatus() == StatusCobranca.PENDENTE).findFirst();

            // Nunca pagou nada ainda (nem o valor original) — só atualiza o valor cheio que
            // falta pagar, igual sempre foi (nenhuma cobrança nova, nenhum estorno).
            if (valorJaPago.compareTo(java.math.BigDecimal.ZERO) == 0) {
                cobrancaPendenteOpt.ifPresent(c -> { c.atualizarValor(precoNovo); cobrancaEventoRepository.save(c); });
                processadas++;
                continue;
            }

            java.math.BigDecimal novoValorDevido = precoNovo.subtract(valorJaPago);
            if (novoValorDevido.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // Falta pagar (ou falta pagar MAIS do que já estava pendente).
                if (cobrancaPendenteOpt.isPresent()) {
                    // Já tinha um complemento pendente de um reajuste anterior — só
                    // atualiza o valor dele, nunca duplica a cobrança.
                    var c = cobrancaPendenteOpt.get();
                    c.atualizarValor(novoValorDevido);
                    cobrancaEventoRepository.save(c);
                    enviarEmailComplementoPagamento(inscricao, c, novoValorDevido);
                } else {
                    try {
                        UUID pessoaId = inscricao.getPessoa() != null ? inscricao.getPessoa().getId() : null;
                        CobrancaEvento complemento = cobrancaEventoService.criarParaTerceiro(
                                inscricao.getIgreja().getId(), eventoId, inscricao.getId(), pessoaId, novoValorDevido, usuarioId, true);
                        // Decisão do usuário (2026-08-27): tratar exatamente como
                        // aplicarEventoVirouPago — a inscrição vira AGUARDANDO_PAGAMENTO até
                        // a diferença ser paga (mesma pendência, mesma tag "Pagamento
                        // pendente" na lista de inscritos, mesmo lembrete/cancelamento por
                        // link — só o texto do e-mail muda).
                        inscricao.setStatus(StatusInscricao.AGUARDANDO_PAGAMENTO);
                        inscricaoRepository.save(inscricao);
                        enviarEmailComplementoPagamento(inscricao, complemento, novoValorDevido);
                    } catch (RuntimeException e) {
                        log.error("Falha ao gerar cobrança de complemento. inscricaoId={}", inscricao.getId(), e);
                        continue;
                    }
                }
            } else {
                // novoValorDevido <= 0: já pagou o suficiente (ou mais) pro novo preço. Se
                // havia um complemento pendente aberto (de um reajuste anterior que subiu o
                // preço), ele não faz mais sentido de jeito nenhum — cancela e confirma de
                // volta, MESMO quando o excedente é zero (achado ao vivo, 2026-08-27: sem
                // isto, um reajuste que zerasse a diferença exatamente deixava a pessoa
                // travada em AGUARDANDO_PAGAMENTO com uma cobrança pendente de um valor que
                // já não era mais devido).
                java.math.BigDecimal excedente = novoValorDevido.abs();
                if (excedente.signum() > 0) {
                    var cobrancaPagaMaisRecente = cobrancas.stream()
                            .filter(c -> c.getStatus() == StatusCobranca.PAGO)
                            .reduce((a, b) -> b); // a mais recente é a última da lista (ordem de criação)
                    if (cobrancaPagaMaisRecente.isEmpty()) continue; // defesa: valorJaPago > 0 implica ter uma
                    var cobrancaParaEstornar = cobrancaPagaMaisRecente.get();
                    try {
                        mercadoPagoClient.estornarParcial(
                                inscricao.getIgreja().getId(), cobrancaParaEstornar.getMpPaymentId(), excedente);
                    } catch (RuntimeException e) {
                        log.error("Falha ao estornar parcialmente. inscricaoId={} mpPaymentId={}",
                                inscricao.getId(), cobrancaParaEstornar.getMpPaymentId(), e);
                        cobrancaParaEstornar.marcarEstornoPendente();
                        cobrancaEventoRepository.save(cobrancaParaEstornar);
                        continue;
                    }
                    cobrancaParaEstornar.registrarEstorno(excedente);
                    cobrancaEventoRepository.save(cobrancaParaEstornar);
                    registrarEstornoNoFinanceiro(inscricao, excedente);
                    enviarEmailEstornoParcial(inscricao, excedente);
                }
                if (cobrancaPendenteOpt.isPresent()) {
                    var c = cobrancaPendenteOpt.get();
                    c.marcarComoCancelado();
                    cobrancaEventoRepository.save(c);
                    inscricao.setStatus(StatusInscricao.CONFIRMADA);
                    inscricaoRepository.save(inscricao);
                } else if (excedente.signum() == 0) {
                    // Nem excedente pra estornar, nem complemento pendente pra cancelar —
                    // já estava exatamente em dia, nada mudou de verdade pra essa pessoa.
                    continue;
                }
            }
            processadas++;
        }

        if (processadas > 0) {
            log.info("Valor do evento pago mudou, inscrições processadas. evento_id={}, processadas={}",
                    eventoId, processadas);
        }
        return processadas;
    }

    private String formatarMoeda(java.math.BigDecimal valor) {
        return java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR")).format(valor);
    }

    private void enviarEmailComplementoPagamento(InscricaoEvento inscricao, CobrancaEvento complemento, java.math.BigDecimal diferenca) {
        String nomeDestinatario;
        String email;
        if (inscricao.getPessoa() != null) {
            nomeDestinatario = inscricao.getPessoa().getNome();
            email = inscricao.getPessoa().getEmail();
        } else {
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }
        if (email == null || email.isBlank()) {
            log.info("Valor do evento aumentou, sem e-mail pra avisar do complemento. inscricaoId={}", inscricao.getId());
            return;
        }

        Evento evento = inscricao.getEvento();
        String link = frontendUrl + "/cobranca/" + complemento.getTokenLinkPublico();
        // Mesmo tratamento de aplicarEventoVirouPago (2026-08-27): a inscrição já virou
        // AGUARDANDO_PAGAMENTO, então este e-mail ganha o mesmo botão de cancelar do
        // lembrete de pagamento pendente comum — cancelar aqui cancela a inscrição de
        // verdade (consistente com o resto do sistema tratando essa pendência igual).
        String linkCancelar = frontendUrl + "/eventos/" + evento.getId() + "/pagamento/" + complemento.getId() + "/cancelar";
        String corpo = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2 style="text-align: center; color: #131b2e;">O valor da inscrição aumentou</h2>
                  <p>Olá, %s.</p>
                  <p>O evento abaixo, em que você está inscrito(a), teve o valor da inscrição reajustado.</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0; font-weight: bold; color: #131b2e;">%s</p>
                  </div>
                  <p>
                    Sua inscrição continua garantida, mas precisa complementar
                    <strong>%s</strong> — pra ficar confirmada de vez. Pague pelo link abaixo:
                  </p>
                  <p style="text-align: center; margin: 24px 0;">
                    <a href="%s" style="display: inline-block; background: #131b2e; color: #fff; padding: 12px 24px; border-radius: 8px; text-decoration: none;">Pagar complemento</a>
                  </p>
                  <p style="text-align: center; margin: 8px 0 0;">
                    <a href="%s" style="color: #64748b; font-size: 13px; text-decoration: underline;">Não vou mais — cancelar inscrição</a>
                  </p>
                </div>
                """.formatted(nomeDestinatario, evento.getTitulo(), formatarMoeda(diferenca), link, linkCancelar);

        try {
            emailService.enviar(email, "Valor da inscrição aumentou — " + evento.getTitulo(), corpo);
            log.info("E-mail de complemento de pagamento enviado. inscricaoId={}", inscricao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de complemento de pagamento. inscricaoId={}", inscricao.getId(), e);
        }
        notificarPessoaSeForUsuario(inscricao, com.domus.api.modules.notificacao.TipoNotificacao.COMPLEMENTO_PAGAMENTO_PENDENTE,
                "O valor de \"" + evento.getTitulo() + "\" aumentou — falta pagar " + formatarMoeda(diferenca) + " a mais.");
    }

    private void enviarEmailEstornoParcial(InscricaoEvento inscricao, java.math.BigDecimal valorEstornado) {
        String nomeDestinatario;
        String email;
        if (inscricao.getPessoa() != null) {
            nomeDestinatario = inscricao.getPessoa().getNome();
            email = inscricao.getPessoa().getEmail();
        } else {
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }
        if (email == null || email.isBlank()) {
            log.info("Valor do evento baixou, sem e-mail pra avisar do estorno parcial. inscricaoId={}", inscricao.getId());
            return;
        }

        Evento evento = inscricao.getEvento();
        String corpo = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2 style="text-align: center; color: #131b2e;">O valor da inscrição mudou</h2>
                  <p>Olá, %s.</p>
                  <p>O evento abaixo, em que você já está confirmado(a), teve o valor da inscrição reduzido.</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0; font-weight: bold; color: #131b2e;">%s</p>
                  </div>
                  <p>
                    A diferença de <strong>%s</strong> será estornada de acordo com o método de
                    pagamento que você utilizou. O prazo depende do seu banco ou operadora do
                    cartão. Sua inscrição continua confirmada normalmente.
                  </p>
                </div>
                """.formatted(nomeDestinatario, evento.getTitulo(), formatarMoeda(valorEstornado));

        try {
            emailService.enviar(email, "Parte do valor foi estornado — " + evento.getTitulo(), corpo);
            log.info("E-mail de estorno parcial enviado. inscricaoId={}", inscricao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de estorno parcial. inscricaoId={}", inscricao.getId(), e);
        }
        notificarPessoaSeForUsuario(inscricao, com.domus.api.modules.notificacao.TipoNotificacao.ESTORNO_PARCIAL_VALOR_EVENTO,
                "O valor de \"" + inscricao.getEvento().getTitulo() + "\" baixou — " + formatarMoeda(valorEstornado) + " será estornado.");
    }

    /**
     * Lembrete de pagamento pendente (2026-08-27) — o gestor pede, a pedido próprio, um
     * empurrão pra quem está com a inscrição em AGUARDANDO_PAGAMENTO há tempo demais.
     * Deliberadamente nunca chamado de "cobrança" no texto/rótulo — é um lembrete, não uma
     * régua de cobrança automática. A inscrição SEMPRE tem uma {@link CobrancaEvento}
     * PENDENTE em aberto enquanto está AGUARDANDO_PAGAMENTO ({@link
     * com.domus.api.modules.pagamento.job.CobrancaEventoExpiracaoJob} cancela a inscrição
     * assim que a cobrança vence) — então não precisa criar uma nova.
     */
    @Transactional
    public void enviarLembretePagamento(UUID inscricaoId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para enviar lembretes de pagamento.");
        }

        InscricaoEvento inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        if (!inscricao.estaAguardandoPagamento()) {
            throw new ConflitoNegocioException("INSCRICAO_NAO_AGUARDA_PAGAMENTO",
                    "Esta inscrição não está com pagamento pendente.");
        }

        List<CobrancaEvento> cobrancasDaInscricao = cobrancaEventoRepository.findByInscricaoId(inscricaoId);
        CobrancaEvento cobranca = cobrancasDaInscricao.stream()
                .filter(c -> c.getStatus() == StatusCobranca.PENDENTE)
                .findFirst()
                .orElseThrow(() -> new ConflitoNegocioException("COBRANCA_NAO_ENCONTRADA",
                        "Não foi encontrada uma cobrança em aberto para esta inscrição."));
        // Diferencia "nunca pagou nada" de "já pagou o valor original, só falta o
        // complemento de um reajuste" — mesma distinção da tag "Falta complementar" na
        // lista de inscritos (2026-08-27), agora também no texto do lembrete.
        boolean pagamentoParcial = cobrancasDaInscricao.stream().anyMatch(c -> c.getStatus() == StatusCobranca.PAGO);

        String nomeDestinatario;
        String email;
        if (inscricao.getPessoa() != null) {
            nomeDestinatario = inscricao.getPessoa().getNome();
            email = inscricao.getPessoa().getEmail();
        } else {
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }
        if (email == null || email.isBlank()) {
            throw new ConflitoNegocioException("SEM_EMAIL_PARA_LEMBRETE",
                    "Esta pessoa não tem e-mail cadastrado para receber o lembrete.");
        }

        Evento evento = inscricao.getEvento();
        String valorFormatado = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("pt", "BR"))
                .format(cobranca.getValor());
        // Cobrança do próprio titular (self-checkout, sem link público) usa a tela
        // autenticada — quem se auto-inscreveu já tem conta no Domus, então faz login pra
        // ver o checkout. As demais (convidado, ou link gerado por terceiro) usam o link
        // público por token, sem exigir login.
        String link = cobranca.getTokenLinkPublico() != null
                ? frontendUrl + "/cobranca/" + cobranca.getTokenLinkPublico()
                : frontendUrl + "/eventos/" + evento.getId() + "/pagamento/" + cobranca.getId();
        // O cancelamento é sempre por cobrancaId (não pelo token) — funciona igual pro
        // titular e pra quem paga por terceiro, ver CobrancaController.cancelarInscricao.
        String linkCancelar = frontendUrl + "/eventos/" + evento.getId() + "/pagamento/" + cobranca.getId() + "/cancelar";

        String paragrafoSituacao = pagamentoParcial
                ? "Você já pagou o valor original da sua inscrição no evento abaixo — só falta a "
                    + "diferença de um reajuste de preço, sua vaga já está garantida."
                : "Sua inscrição no evento abaixo ainda está aguardando pagamento.";
        String paragrafoValor = pagamentoParcial
                ? "Falta complementar <strong>%s</strong>. Pague pelo link abaixo:".formatted(valorFormatado)
                : "Pra garantir sua vaga de vez, falta pagar <strong>%s</strong>. Pague pelo link abaixo:".formatted(valorFormatado);
        String corpo = """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2 style="text-align: center; color: #131b2e;">Lembrete de pagamento pendente</h2>
                  <p>Olá, %s.</p>
                  <p>%s</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0; font-weight: bold; color: #131b2e;">%s</p>
                  </div>
                  <p>
                    %s
                  </p>
                  <p style="text-align: center; margin: 24px 0;">
                    <a href="%s" style="display: inline-block; background: #131b2e; color: #fff; padding: 12px 24px; border-radius: 8px; text-decoration: none;">Efetuar pagamento</a>
                  </p>
                  <p style="text-align: center; margin: 8px 0 0;">
                    <a href="%s" style="color: #64748b; font-size: 13px; text-decoration: underline;">Não vou mais — cancelar inscrição</a>
                  </p>
                </div>
                """.formatted(nomeDestinatario, paragrafoSituacao, evento.getTitulo(), paragrafoValor, link, linkCancelar);

        emailService.enviar(email, "Lembrete de pagamento pendente — " + evento.getTitulo(), corpo);
        log.info("Lembrete de pagamento enviado. inscricaoId={}, igrejaId={}", inscricaoId, igrejaId);
        notificarPessoaSeForUsuario(inscricao, com.domus.api.modules.notificacao.TipoNotificacao.LEMBRETE_PAGAMENTO_PENDENTE,
                "Lembrete: falta pagar " + valorFormatado + " pra confirmar sua inscrição em \"" + evento.getTitulo() + "\".");
    }
}
