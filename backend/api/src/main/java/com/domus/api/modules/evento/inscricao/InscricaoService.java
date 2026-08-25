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
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteResponse;
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
    private final AcompanhanteRepository acompanhanteRepository;
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

        if (inscricao != null) {
            inscricao.setStatus(StatusInscricao.CONFIRMADA);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
            inscricao.setInscritoPorExcecao(porExcecao);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .pessoa(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(StatusInscricao.CONFIRMADA)
                    .inscritoPorExcecao(porExcecao)
                    .build();
        }

        InscricaoEvento salva = inscricaoRepository.save(inscricao);

        // Evento pago: titular sempre "eu pago agora" (nunca vira link, diferente do
        // acompanhante em adicionarAcompanhante) — vaga fica reservada via CobrancaEvento
        // (ver validarVaga), não pela InscricaoEvento CONFIRMADA (essa é sempre imediata,
        // pago ou não). Quem assina como "criado por": quem inscreveu (admin/líder em
        // lote) ou, na auto-inscrição (inscritoPorOuNull nulo), o próprio usuário do
        // titular — sempre existe, pois só se auto-inscreve quem está logado.
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
                    ? cobrancaEventoService.criarParaTerceiro(igrejaId, eventoId, salva.getId(), pessoaId, null,
                            evento.getPreco(), criadoPorUsuarioId, true)
                    : cobrancaEventoService.criarParaTitular(igrejaId, eventoId, salva.getId(), pessoaId,
                            evento.getPreco(), criadoPorUsuarioId);
        }

        // Nem quando o responsável se auto-inscreve, nem quando ele mesmo inscreve outra pessoa —
        // quem fez a ação já sabe dela.
        if (evento.getResponsavel() != null && !evento.getResponsavel().getId().equals(pessoaId)) {
            usuarioRepository.findByPessoaId(evento.getResponsavel().getId())
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
                    r.cobranca() != null ? r.cobranca().getId() : null,
                    r.cobranca() != null ? r.cobranca().getTokenLinkPublico() : null));
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID pessoaId) {
        return inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(InscricaoEvento::estaConfirmada)
                .map(i -> MinhaInscricaoResponse.from(i, cobrancaPendenteDoTitular(i.getId(), pessoaId)))
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }

    /**
     * Task 14: se a pessoa recarregar a página antes de pagar (ou fechar o Brick sem
     * concluir), {@code minhaInscricao} precisa continuar devolvendo o id da cobrança
     * pendente do TITULAR — nunca a de um acompanhante, que segue um fluxo à parte
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

    /** Compara por telefone normalizado, com fallback para nome se não houver telefone. */
    private void validarConvidadoNaoDuplicado(UUID eventoId, AcompanhanteRequest data) {
        String telefoneNovo = TextoUtil.somenteDigitos(data.telefone());
        String nomeNovo = TextoUtil.normalizarParaComparacao(data.nome());

        for (AcompanhanteInscricao existente : acompanhanteRepository.listarPorEvento(eventoId)) {
            if (mesmoConvidado(telefoneNovo, nomeNovo, existente.getTelefone(), existente.getNome())) {
                throw new BusinessException("CONVIDADO_DUPLICADO",
                        "Este convidado já está inscrito neste evento.");
            }
        }
    }

    /** Mesma checagem de {@link #validarConvidadoNaoDuplicado}, mas para o convidado de topo
     *  (sem Pessoa, sem acompanhante-pai) — antes disto, buscar o mesmo visitante/pessoa de
     *  fora duas vezes no modal "Inscrever alguém" (ou no convite público) criava duas
     *  inscrições separadas, ocupando duas vagas pra mesma pessoa. Checa tanto contra outros
     *  convidados de topo quanto contra acompanhantes já cadastrados no evento. */
    /** Mesma mensagem dupla de {@link #inscrever} (JA_INSCRITO): quem preenche o próprio
     *  formulário pelo link ({@code inscritoPorUsuarioId == null}) vê "você"; quem é
     *  cadastrado por outra pessoa pelo sistema vê "essa pessoa". */
    private void validarConvidadoTopoNaoDuplicado(UUID eventoId, String nome, String telefone,
                                                   UUID visitanteId, UUID inscritoPorUsuarioId) {
        List<InscricaoEvento> convidadosNoTopo = inscricaoRepository.listarConvidadosSemCadastroPorEvento(eventoId);

        // Com visitanteId, a checagem é exata (o mesmo Visitante já está inscrito) — não
        // depende de nome/telefone baterem, que é frágil (apelido, telefone desatualizado…).
        boolean duplicadoPorVisitante = visitanteId != null && convidadosNoTopo.stream()
                .anyMatch(i -> i.getVisitante() != null && i.getVisitante().getId().equals(visitanteId));

        String telefoneNovo = TextoUtil.somenteDigitos(telefone);
        String nomeNovo = TextoUtil.normalizarParaComparacao(nome);

        boolean duplicadoNoTopo = convidadosNoTopo.stream()
                .anyMatch(i -> mesmoConvidado(telefoneNovo, nomeNovo, i.getTelefoneConvidado(), i.getNomeConvidado()));
        boolean duplicadoComoAcompanhante = acompanhanteRepository.listarPorEvento(eventoId).stream()
                .anyMatch(a -> mesmoConvidado(telefoneNovo, nomeNovo, a.getTelefone(), a.getNome()));

        if (duplicadoPorVisitante || duplicadoNoTopo || duplicadoComoAcompanhante) {
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
     * gratuito continua exatamente como antes: conta inscrições confirmadas + acompanhantes.
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
    @Transactional
    public InscricaoEvento inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
                                               String telefone, UUID convidadoPorPessoaId,
                                               UUID inscritoPorUsuarioId, UUID visitanteId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        validarOrganizaInscricao(evento, "Este evento não permite convidados.");
        if (evento.isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        // Convidado de topo não tem Pessoa nem é um acompanhante — CobrancaEvento exige um
        // dos dois (ver construtor), então não há como gerar cobrança pra ele hoje. Sem essa
        // trava, evento pago poderia ser "furado" inscrevendo qualquer um por aqui, sem pagar
        // nada, e essa vaga ficaria invisível pra contarPessoasComVagaReservada (ver
        // contarOcupadas) — overbooking real. Bloqueia até existir uma forma de cobrar
        // convidado sem cadastro (fora do escopo desta task).
        if (evento.getPreco() != null) {
            throw new BusinessException("CONVIDADO_NAO_PODE_EM_EVENTO_PAGO",
                    "Este evento é pago — inscreva com uma pessoa cadastrada para gerar a cobrança.");
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

        InscricaoEvento inscricao = InscricaoEvento.builder()
                .igreja(evento.getIgreja())
                .evento(evento)
                .pessoa(null)
                .nomeConvidado(TextoUtil.capitalizar(nome))
                .telefoneConvidado(telefone)
                .convidadoPor(convidadoPor)
                .visitante(visitante)
                .inscritoPorUsuarioId(inscritoPorUsuarioId)
                .status(StatusInscricao.CONFIRMADA)
                .build();

        InscricaoEvento salva = inscricaoRepository.save(inscricao);
        log.info("Convidado inscrito. evento_id={}, convidado_por_pessoa_id={}, inscrito_por_usuario_id={}, igreja_id={}",
                eventoId, convidadoPorPessoaId, inscritoPorUsuarioId, igrejaId);
        return salva;
    }

    /** Convidado de fora, pendurado na inscrição de quem o trouxe. Ocupa vaga. */
    @Transactional
    public AcompanhanteResponse adicionarAcompanhante(UUID inscricaoId, AcompanhanteRequest data,
                                                       UUID usuarioId, UUID meuMembroId, String role,
                                                       UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean gestorDaMesmaIgreja = ehGestor && inscricao.getIgreja().getId().equals(igrejaId);
        boolean souODono = inscricao.getPessoa() != null && inscricao.getPessoa().getId().equals(meuMembroId);
        if (!gestorDaMesmaIgreja && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode adicionar convidados à sua própria inscrição.");
        }

        validarOrganizaInscricao(inscricao.getEvento(), "Este evento não permite convidados.");

        if (inscricao.getEvento().isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(inscricao.getEvento());
        validarConvidadoNaoDuplicado(inscricao.getEvento().getId(), data);

        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        eventoRepository.buscarComLockVisivelParaFamilia(inscricao.getEvento().getId(), igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarVaga(inscricao.getEvento(), 1);

        AcompanhanteInscricao a = AcompanhanteInscricao.builder()
                .inscricao(inscricao)
                .nome(TextoUtil.capitalizar(data.nome()))
                .telefone(data.telefone())
                .build();

        AcompanhanteInscricao salvo = acompanhanteRepository.save(a);

        // Terceiro (não o titular): a escolha de "pagar agora" vs. "gerar link" vem do
        // próprio AcompanhanteRequest (campo gerarLinkPagamento) — quem está adicionando o
        // convidado decide se cobra na hora ou manda um link pra essa pessoa pagar sozinha.
        if (inscricao.getEvento().getPreco() != null) {
            cobrancaEventoService.criarParaTerceiro(igrejaId, inscricao.getEvento().getId(),
                    inscricaoId, null, salvo.getId(), inscricao.getEvento().getPreco(),
                    usuarioId, data.gerarLinkPagamento());
        }

        log.info("Acompanhante adicionado. inscricao_id={}, por_usuario={}, igreja_id={}",
                inscricaoId, usuarioId, igrejaId);
        return AcompanhanteResponse.from(salvo);
    }

    @Transactional
    public void removerAcompanhante(UUID acompanhanteId, UUID meuMembroId, String role, UUID igrejaId) {
        AcompanhanteInscricao a = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        InscricaoEvento inscricao = a.getInscricao();
        if (!familiaIgrejaService.idsDaFamiliaCompleta(igrejaId).contains(inscricao.getIgreja().getId())) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        // Mesma trava de cancelar(): evento EM_ANDAMENTO/ENCERRADO reescreveria quem esteve presente.
        validarEventoAberto(inscricao.getEvento());

        // Permissão vem de ser dono da inscrição, não de quem inscreveu — inscritoPorUsuarioId é NULL na maioria dos casos.
        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean gestorDaMesmaIgreja = ehGestor && inscricao.getIgreja().getId().equals(igrejaId);
        boolean souODono = inscricao.getPessoa() != null && inscricao.getPessoa().getId().equals(meuMembroId);

        if (!gestorDaMesmaIgreja && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode remover convidados da sua própria inscrição.");
        }
        acompanhanteRepository.delete(a);
        log.info("Acompanhante removido. acompanhante_id={}, por_membro={}, igreja_id={}",
                acompanhanteId, meuMembroId, igrejaId);
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

        int convidados = inscricao.getAcompanhantes().size();
        cancelarInterno(inscricao);
        log.info("Inscrição cancelada. id={}, convidados_removidos={}, por_usuario={}, igreja_id={}",
                inscricaoId, convidados, usuarioId, igrejaId);
    }

    /** Reusado pelo cancelamento manual e pela remoção por restrição — convidados não voltam
     *  numa reinscrição, e as respostas de campo personalizado também não: a linha de
     *  inscrição é reaproveitada (UNIQUE evento+pessoa), então sem isso a reinscrição
     *  herdaria resposta velha e pareceria "já respondido" sem a pessoa ter respondido nada
     *  desta vez. */
    private void cancelarInterno(InscricaoEvento inscricao) {
        // Roda ANTES de marcar CANCELADA: se o estorno falhar, o BusinessException aborta a
        // transação inteira e a inscrição continua CONFIRMADA — nunca "cancelada no Domus"
        // com o dinheiro ainda retido no Mercado Pago.
        estornarCobrancasDaInscricao(inscricao);
        inscricao.getAcompanhantes().clear();   // orphanRemoval = true apaga as linhas
        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
        respostaCampoPersonalizadoRepository.deleteByInscricaoId(inscricao.getId());
    }

    /** PAGO estorna de verdade no Mercado Pago; PENDENTE só cancela (nunca chegou a ser cobrado). */
    private void estornarCobrancasDaInscricao(InscricaoEvento inscricao) {
        List<CobrancaEvento> cobrancas = cobrancaEventoRepository.findByInscricaoId(inscricao.getId());
        if (cobrancas.isEmpty()) return;

        UUID igrejaId = inscricao.getIgreja().getId();
        for (CobrancaEvento cobranca : cobrancas) {
            if (cobranca.getStatus() == StatusCobranca.PAGO) {
                try {
                    mercadoPagoClient.estornar(igrejaId, cobranca.getMpPaymentId());
                    cobranca.marcarComoReembolsado();
                } catch (Exception e) {
                    throw new BusinessException("FALHA_ESTORNO",
                            "FALHA_ESTORNO: não foi possível estornar o pagamento. Tente novamente em instantes.");
                }
            } else if (cobranca.getStatus() == StatusCobranca.PENDENTE) {
                cobranca.marcarComoCancelado();
            }
        }
        cobrancaEventoRepository.saveAll(cobrancas);
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

        List<InscritoResponse> inscritosDaPagina = inscricoes.stream()
                .map(i -> InscritoResponse.from(i,
                        resolverPessoa(i, pessoas),
                        registrantes.get(i.getInscritoPorUsuarioId()),
                        resolverConvidadoPor(i, pessoas)))
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
     * @return quantas pessoas físicas (inscritos + acompanhantes) foram marcadas.
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
            for (AcompanhanteInscricao acompanhante : inscricao.getAcompanhantes()) {
                acompanhante.setCompareceu(true);
                marcados++;
            }
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
            for (AcompanhanteInscricao acompanhante : inscricao.getAcompanhantes()) {
                acompanhante.setCompareceu(false);
                desmarcados++;
            }
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

    /** Corrige a exceção de UM convidado específico (o inscrito veio, o convidado não, ou vice-versa). */
    @Transactional
    public void marcarPresencaAcompanhante(UUID eventoId, UUID acompanhanteId, boolean compareceu,
                                           UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        AcompanhanteInscricao acompanhante = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        // Mesmo isolamento de removerAcompanhante(): id de outra igreja é tratado como inexistente, nunca vaza.
        if (!acompanhante.getInscricao().getIgreja().getId().equals(igrejaId)) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        validarControlaPresenca(acompanhante.getInscricao().getEvento());
        validarInscricaoConfirmada(acompanhante.getInscricao());

        acompanhante.setCompareceu(compareceu);
        acompanhanteRepository.save(acompanhante);
        log.info("Presença de convidado marcada. acompanhante_id={}, compareceu={}, igreja_id={}",
                acompanhanteId, compareceu, igrejaId);
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
}
