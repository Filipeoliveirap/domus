package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.DTOs.ImpactoRestricaoResponse;
import com.domus.api.modules.evento.elegibilidade.Elegibilidade;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.NaoElegivelException;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.InscritoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ListaInscritosResponse;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ParticipanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InscricaoService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final AcompanhanteRepository acompanhanteRepository;
    private final PessoaRepository membroRepository;
    private final UsuarioRepository usuarioRepository;
    private final ElegibilidadeService elegibilidadeService;

    /**
     * Inscreve um membro. {@code inscritoPorOuNull} é NULL na auto-inscrição.
     *
     * <p><b>Auto-inscrição funciona em QUALQUER evento</b> — é leve, tipo uma curtida ("eu
     * vou"). {@code requerInscricao} não bloqueia mais este método; ele passou a significar
     * só "este evento organiza vagas, convidados e inscrição de terceiros" (ver
     * {@link #inscreverPessoas} e {@link #adicionarAcompanhante}, que continuam checando).
     *
     * <p>O evento é buscado COM LOCK: a contagem de vagas e o insert precisam ser atômicos,
     * senão duas inscrições simultâneas na última vaga passam as duas. {@code validarVaga}
     * já não faz nada quando {@code vagas} é NULL, que é o caso comum de evento casual —
     * então a auto-inscrição neles nunca esbarra em limite de vaga.
     *
     * <p>{@code role}/{@code confirmado} só importam quando {@code inscritoPorOuNull != null}
     * (inscrevendo um TERCEIRO): é a única situação em que a elegibilidade pode ser contornada
     * por quem gerencia. Na auto-inscrição os dois são ignorados — ver Javadoc de
     * {@link #validarElegibilidade}.
     *
     * <p>{@code minhaPessoaId} é a pessoa do USUÁRIO LOGADO (nunca do corpo da requisição).
     * É contra ela — não contra {@code inscritoPorOuNull} — que decidimos se isto é
     * auto-inscrição: ver Javadoc de {@link #validarElegibilidade} para o porquê.
     */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID pessoaId, UUID inscritoPorOuNull,
                                            UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        Evento evento = eventoRepository.buscarComLock(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Pessoa membro = membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        validarEventoAberto(evento);
        boolean porExcecao = validarElegibilidade(evento, membro, role, confirmado);

        InscricaoEvento inscricao = inscricaoRepository
                .findByEventoIdAndPessoaId(eventoId, pessoaId)
                .orElse(null);

        if (inscricao != null && inscricao.estaConfirmada()) {
            // Mesmo código (JA_INSCRITO) nos dois casos: quem chama já sabe o contexto (botão
            // "eu vou" vs. modal de inscrever outra pessoa), então a mensagem só precisa ter
            // as palavras certas — não é o front que precisa distinguir por código.
            String mensagem = inscritoPorOuNull == null
                    ? "Você já está inscrito neste evento."
                    : "Este membro já está inscrito no evento.";
            throw new BusinessException("JA_INSCRITO", mensagem);
        }

        validarVaga(evento, 1);

        if (inscricao != null) {
            // Reaproveita a linha cancelada: o UNIQUE (evento_id, pessoa_id) impediria inserir
            // outra, e sem isto quem cancelasse ficaria impedido de voltar ao próprio evento.
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
        log.info("Inscrição confirmada. evento_id={}, pessoa_id={}, inscrito_por={}, igreja_id={}",
                eventoId, pessoaId, inscritoPorOuNull, igrejaId);
        return MinhaInscricaoResponse.from(salva);
    }

    /**
     * Inscreve vários membros de uma vez (modal de seleção múltipla) — inscrição de
     * TERCEIROS, então continua exigindo {@code requerInscricao}.
     *
     * <p><b>Tudo ou nada, por decisão:</b> se um membro falhar (ex.: já inscrito), a transação
     * inteira volta atrás e nenhum é inscrito. Resultado parcial exigiria DTO e tela próprios
     * para um caso que ainda não sabemos se acontece.
     *
     * <p>Os "já inscritos" são checados ANTES do laço (uma query só, com os ids todos) para
     * poder nomear a QUANTIDADE na mensagem — se deixássemos o laço descobrir um de cada vez
     * via {@link #inscrever}, o erro só falaria do primeiro que bateu, nunca do total.
     *
     * <p>{@code confirmado} só faz efeito se {@code role} tiver
     * {@link Permissoes#podeGerenciarInscricoes(String)} — de quem NÃO gerencia, o parâmetro
     * é simplesmente ignorado (nunca aceito "por engano"), checagem que vive dentro de
     * {@link #validarElegibilidade}, não aqui.
     */
    @Transactional
    public void inscreverPessoas(UUID eventoId, List<UUID> pessoaIds, UUID inscritoPorUsuarioId,
                                 UUID minhaPessoaId, String role, boolean confirmado, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
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

        for (UUID pessoaId : pessoaIds) {
            // minhaPessoaId segue até inscrever() SEM alteração: é lá, no único ponto que
            // decide "isto é auto-inscrição?", que pessoaId (alvo) é comparado com ela — não
            // com inscritoPorUsuarioId (id de USUÁRIO, nunca de pessoa; comparar os dois nunca
            // bateria por acidente, e foi exatamente isso que escondeu o furo antes desta correção).
            inscrever(eventoId, pessoaId, inscritoPorUsuarioId, minhaPessoaId, role, confirmado, igrejaId);
        }
    }

    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID pessoaId) {
        return inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(InscricaoEvento::estaConfirmada)
                .map(MinhaInscricaoResponse::from)
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }

    /**
     * Checa se o evento organiza inscrição de TERCEIROS (vagas, convidados, inscrever outra
     * pessoa) — {@code requerInscricao}. NÃO se aplica à auto-inscrição (ver {@link #inscrever}),
     * que funciona em qualquer evento. Mensagem varia por chamador para dizer exatamente o que
     * está indisponível, em vez do genérico "não abre inscrição" (que ficou errado desde que a
     * auto-inscrição passou a valer sempre).
     */
    private void validarOrganizaInscricao(Evento evento, String mensagem) {
        if (!evento.isRequerInscricao()) {
            throw new BusinessException("INSCRICAO_NAO_HABILITADA", mensagem);
        }
    }

    /**
     * B3: bloqueia inscrição/convidado tanto em evento EM_ANDAMENTO quanto ENCERRADO —
     * mensagens DIFERENTES porque são fatos diferentes ("já começou" não é o mesmo que "já
     * aconteceu"). Antes disto, {@code inicioEm < agora} tratava os dois casos como
     * "encerrado", o que é factualmente errado para um evento que está rolando agora.
     */
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

    /**
     * B1: bloqueia o MESMO convidado duas vezes NO MESMO EVENTO — o dano real é ocupar duas
     * vagas. Fora do evento não faz sentido bloquear (a mesma pessoa pode ir a vários eventos),
     * então a comparação é sempre restrita a {@code eventoId}.
     *
     * <p>Telefone é opcional, então a comparação principal é por telefone (normalizado para só
     * dígitos — o front manda formatado e o que já está salvo pode estar inconsistente); sem
     * telefone em QUALQUER um dos dois lados, cai para nome normalizado (sem acento, sem case,
     * espaços colapsados).
     *
     * <p><b>Por que checagem no service, não constraint de banco:</b> a regra combina duas
     * comparações diferentes (telefone OU nome) e a de nome depende de normalização (remover
     * acento) que o Postgres não expressa como expressão de índice de forma simples/portável.
     * Um índice único parcial em telefone normalizado por evento até seria possível, mas não
     * cobriria o caminho por nome — e ter a UNIQUE cobrindo só metade da regra escondida atrás
     * de uma exceção de banco (em vez de uma mensagem de negócio clara) seria pior que não ter.
     */
    private void validarConvidadoNaoDuplicado(UUID eventoId, AcompanhanteRequest data) {
        String telefoneNovo = TextoUtil.somenteDigitos(data.telefone());
        String nomeNovo = TextoUtil.normalizarParaComparacao(data.nome());

        for (AcompanhanteInscricao existente : acompanhanteRepository.listarPorEvento(eventoId)) {
            String telefoneExistente = TextoUtil.somenteDigitos(existente.getTelefone());
            boolean duplicado = telefoneNovo != null && telefoneExistente != null
                    ? telefoneNovo.equals(telefoneExistente)
                    : nomeNovo != null && nomeNovo.equals(TextoUtil.normalizarParaComparacao(existente.getNome()));

            if (duplicado) {
                throw new BusinessException("CONVIDADO_DUPLICADO",
                        "Este convidado já está inscrito neste evento.");
            }
        }
    }

    /**
     * Roda todas as regras de {@link ElegibilidadeService} (faixa etária, vínculo, sexo,
     * estado civil...) e decide se o impedimento pode ser contornado.
     *
     * <p><b>Regra 1 — auto-inscrição NUNCA contorna, nem para quem gerencia.</b> A exceção
     * existe para inscrever TERCEIROS (equipe, preletor, motorista); se o próprio admin
     * pudesse burlar a própria inscrição, a restrição viraria decoração para quem tem acesso.
     * {@code autoInscricao} é decidido em {@link #inscrever} comparando a PESSOA ALVO com a
     * pessoa do usuário logado — NUNCA com {@code inscritoPorOuNull}/{@code
     * inscritoPorUsuarioId}, que é id de USUÁRIO, sempre não-nulo em {@link #inscreverPessoas}
     * (o controller manda {@code usuario.getId()} mesmo quando o admin bota o PRÓPRIO
     * pessoaId na lista). Usar esse campo como sinal de auto-inscrição é exatamente o furo
     * que já existiu aqui: {@code inscritoPorOuNull == null} nunca é verdade em
     * {@code inscreverPessoas}, então "auto-inscrição nunca contorna" silenciosamente virava
     * "auto-inscrição contorna igual a qualquer terceiro" para quem passasse pelo modal em vez
     * da rota de auto-inscrição.
     *
     * <p><b>Regra 2 — {@code confirmado=true} de quem NÃO gerencia é IGNORADO</b>, não aceito:
     * {@link Permissoes#podeGerenciarInscricoes(String)} é checado ANTES de olhar
     * {@code confirmado}, então o parâmetro nunca é decisivo sozinho.
     *
     * <p><b>Regra 3 — vaga não entra aqui.</b> {@code VAGAS_ESGOTADAS} não é produzido por
     * {@link ElegibilidadeService} (ver Javadoc dele) — quem barra vaga é
     * {@link #validarVaga}, sempre, sem exceção administrativa.
     *
     * <p><b>Regra 4 — quem NÃO gerencia não vê nome/idade de terceiro no 422.</b> ACESSO_COMUM
     * pode chamar {@code POST .../inscricoes/pessoas} com um {@code pessoaId} arbitrário da
     * igreja; o impedimento é sempre recusado para ele (Regra 2), mas a MENSAGEM não pode virar
     * um oráculo de dados pessoais. Só quem {@link Permissoes#podeGerenciarInscricoes(String)}
     * vê o texto detalhado (ele já tem acesso à lista de pessoas e precisa da informação para
     * decidir sobre o contorno).
     *
     * @return {@code true} quando a inscrição só existe porque um impedimento foi contornado
     *         deliberadamente ("inscrever mesmo assim") — vira a marca DURÁVEL
     *         {@link InscricaoEvento#isInscritoPorExcecao()} (Task 6), que protege esta
     *         inscrição de ser cancelada em silêncio numa edição futura do evento.
     */
    private boolean validarElegibilidade(Evento evento, Pessoa membro, String role, boolean confirmado) {
        Elegibilidade elegibilidade = elegibilidadeService.avaliar(evento, membro);
        if (elegibilidade.apto()) return false;

        // Quem GERENCIA inscrições (admin/líder) pode contornar uma restrição contornável,
        // inclusive na PRÓPRIA inscrição — decisão do autor: o gestor organiza os eventos e
        // pode participar de um recorte fora do seu (equipe do retiro de jovens, café dos
        // homens que ele coordena). Exige `confirmado` explícito por clique, então não é
        // burla casual. Quem NÃO gerencia nunca contorna (podeGerenciar == false barra),
        // então a restrição continua real para o membro comum — que era a proteção que
        // importava. VAGAS_ESGOTADAS não é contornável (não entra em totalmenteContornavel).
        boolean podeGerenciar = Permissoes.podeGerenciarInscricoes(role);
        boolean podeContornar = podeGerenciar
                && confirmado
                && elegibilidade.totalmenteContornavel();

        if (!podeContornar) {
            throw NaoElegivelException.para(elegibilidade.impedimentos(), podeGerenciar);
        }
        return true;
    }

    /**
     * Quantas pessoas (inscritos confirmados + acompanhantes) ocupam vaga hoje neste evento.
     * Exposto para o {@link com.domus.api.modules.evento.EventoService} usar na A9 (recusar
     * reduzir {@code vagas} abaixo de quem já está confirmado) sem duplicar a query.
     */
    @Transactional(readOnly = true)
    public long contarPessoasConfirmadas(UUID eventoId) {
        return inscricaoRepository.contarPessoasConfirmadas(eventoId);
    }

    /** {@code vagas == null} significa sem limite. */
    void validarVaga(Evento evento, int pessoasAAdicionar) {
        if (evento.getVagas() == null) return;

        long ocupadas = inscricaoRepository.contarPessoasConfirmadas(evento.getId());
        if (ocupadas + pessoasAAdicionar > evento.getVagas()) {
            throw new BusinessException("VAGAS_ESGOTADAS",
                    "As vagas deste evento estão esgotadas.");
        }
    }

    /** Convidado de fora, pendurado na inscrição de quem o trouxe. Ocupa vaga. */
    @Transactional
    public AcompanhanteResponse adicionarAcompanhante(UUID inscricaoId, AcompanhanteRequest data,
                                                      UUID usuarioId, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        // Alcançável: a inscrição pode existir de quando o evento AINDA aceitava inscrição,
        // e o admin desligar o toggle depois. Sem esta checagem, o evento fecharia as
        // inscrições e continuaria aceitando convidados — que ocupam vaga igual.
        validarOrganizaInscricao(inscricao.getEvento(), "Este evento não permite convidados.");

        if (inscricao.getEvento().isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(inscricao.getEvento());
        validarConvidadoNaoDuplicado(inscricao.getEvento().getId(), data);

        // Trava o evento antes de contar: mesma corrida da inscrição.
        eventoRepository.buscarComLock(inscricao.getEvento().getId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarVaga(inscricao.getEvento(), 1);

        AcompanhanteInscricao a = AcompanhanteInscricao.builder()
                .inscricao(inscricao)
                .nome(TextoUtil.capitalizar(data.nome()))
                .telefone(data.telefone())
                .build();

        AcompanhanteInscricao salvo = acompanhanteRepository.save(a);
        log.info("Acompanhante adicionado. inscricao_id={}, por_usuario={}, igreja_id={}",
                inscricaoId, usuarioId, igrejaId);
        return AcompanhanteResponse.from(salvo);
    }

    @Transactional
    public void removerAcompanhante(UUID acompanhanteId, UUID meuMembroId, String role, UUID igrejaId) {
        AcompanhanteInscricao a = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        InscricaoEvento inscricao = a.getInscricao();
        if (!inscricao.getIgreja().getId().equals(igrejaId)) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        // A2: mesma trava de cancelar() — remover convidado de evento EM_ANDAMENTO/ENCERRADO
        // reescreveria quem esteve presente. Vale para TODO MUNDO, admin incluso (ver Javadoc
        // de cancelar()).
        validarEventoAberto(inscricao.getEvento());

        // A permissão vem de SER DONO DA INSCRIÇÃO, não de ter sido quem inscreveu.
        // Comparar com inscritoPorUsuarioId seria furo: ele é NULL em toda auto-inscrição
        // (o caso mais comum), e qualquer NULL-check liberaria geral.
        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean souODono = inscricao.getPessoa().getId().equals(meuMembroId);

        if (!ehGestor && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode remover convidados da sua própria inscrição.");
        }
        acompanhanteRepository.delete(a);
        log.info("Acompanhante removido. acompanhante_id={}, por_membro={}, igreja_id={}",
                acompanhanteId, meuMembroId, igrejaId);
    }

    /**
     * Cancela uma inscrição.
     *
     * <p>Regra: você controla VOCÊ MESMO e o que você trouxe. Quem inscreveu alguém
     * NÃO pode desinscrever — inscrever ocupa uma vaga, mas desinscrever tira a pessoa de
     * um evento que ela achava que ia, e ela só descobre no dia.
     *
     * <p><b>A2 — vale para ADMIN/LÍDER também, sem exceção.</b> Cogitou-se liberar o admin
     * para "corrigir um erro de digitação logo depois do evento", mas {@code validarEventoAberto}
     * só enxerga a situação do evento (AGENDADO/EM_ANDAMENTO/ENCERRADO) — não tem noção de
     * "isso acabou de terminar" vs. "isso terminou há três meses". Uma exceção para admin seria
     * tudo ou nada: o mesmo caminho que corrige um engano no dia também apagaria, em silêncio,
     * quem esteve num evento de meses atrás. Presença é histórico, e a igreja pode precisar
     * dele depois (frequência, relatório, até prova de participação). Se um dia for preciso um
     * ajuste pós-evento de verdade, isso é uma feature própria e auditada (motivo obrigatório,
     * log de quem e por quê) — não uma brecha silenciosa no cancelamento normal.
     */
    @Transactional
    public void cancelar(UUID inscricaoId, UUID usuarioId, UUID meuMembroId,
                         String role, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);
        validarEventoAberto(inscricao.getEvento());

        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
        boolean souEu = inscricao.getPessoa().getId().equals(meuMembroId);

        if (!ehGestor && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode cancelar a inscrição de outra pessoa. "
                    + "Peça a ela ou a um líder da igreja.");
        }

        int convidados = inscricao.getAcompanhantes().size();
        cancelarInterno(inscricao);
        log.info("Inscrição cancelada. id={}, convidados_removidos={}, por_usuario={}, igreja_id={}",
                inscricaoId, convidados, usuarioId, igrejaId);
    }

    /**
     * O cancelamento em si, sem checagem de permissão — reusado tanto pelo cancelamento manual
     * ({@link #cancelar}) quanto pela remoção automática ao restringir o evento
     * ({@link #removerInscritosNaoElegiveis}), para as duas vias levarem os convidados junto
     * do mesmo jeito.
     *
     * <p>Os convidados vão embora com a inscrição, e NÃO voltam numa reinscrição. Sem isto,
     * quem cancelou porque o convidado desistiu o veria reaparecer em silêncio ao se
     * reinscrever — ocupando vaga de novo. Quem quiser levá-lo de novo cadastra de novo; é o
     * passo consciente que o silêncio não teria.
     */
    private void cancelarInterno(InscricaoEvento inscricao) {
        inscricao.getAcompanhantes().clear();   // orphanRemoval = true apaga as linhas
        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
    }

    /**
     * Cancela, dos CONFIRMADOS de hoje, quem não é mais elegível para a configuração ATUAL do
     * evento — mesma regra de {@link #validarElegibilidade} (via
     * {@link ElegibilidadeService#avaliar}), mesmo cancelamento de {@link #cancelarInterno}
     * (leva os convidados junto).
     *
     * <p><b>Task 6 — só roda com escolha EXPLÍCITA do admin</b> ({@code cancelarNaoElegiveis=true}
     * no {@code PUT /eventos/{id}}), nunca mais automaticamente ao salvar o evento. Apertar uma
     * faixa etária ou ligar {@code exclusivoMembros} sozinho NÃO cancela mais ninguém — cancelar
     * em silêncio apagaria as exceções que o próprio admin abriu com "inscrever mesmo assim"
     * (ver {@link EventoService#calcularImpacto} para a prévia que substitui o cancelamento
     * automático).
     *
     * <p><b>Preserva a exceção deliberada:</b> pula quem tem
     * {@link InscricaoEvento#isInscritoPorExcecao()} — o motorista CONGREGANTE inscrito de
     * propósito num evento exclusivo continua confirmado mesmo com {@code cancelarNaoElegiveis=true},
     * porque a marca V5 é justamente o registro de que aquela irregularidade é intencional, não
     * um efeito colateral da regra mudar.
     *
     * @return quantas inscrições foram canceladas, para o chamador logar/devolver ao front.
     */
    @Transactional
    public int removerInscritosNaoElegiveis(UUID eventoId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int removidos = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            if (inscricao.isInscritoPorExcecao()) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(
                    inscricao.getEvento(), inscricao.getPessoa());
            if (!elegibilidade.apto()) {
                cancelarInterno(inscricao);
                removidos++;
            }
        }

        if (removidos > 0) {
            log.info("Inscrições removidas por restrição de evento (escolha explícita). "
                    + "evento_id={}, removidos={}", eventoId, removidos);
        }
        return removidos;
    }

    /**
     * Prévia PURA (nada é gravado) de quem, dentre os CONFIRMADOS de hoje, ficaria de fora sob
     * {@code regrasHipoteticas} — alimenta {@code POST /eventos/{id}/impacto-restricao}
     * (ver {@link EventoService#calcularImpacto}).
     *
     * <p>Pula quem já tem {@link InscricaoEvento#isInscritoPorExcecao()}: essas exceções são
     * permanentes por decisão do admin, então nunca aparecem como "afetadas" nem sob regra mais
     * apertada — o admin já escolheu, uma vez, mantê-las.
     */
    @Transactional(readOnly = true)
    public List<ImpactoRestricaoResponse.InscritoImpactado> calcularImpacto(
            UUID eventoId, Evento regrasHipoteticas) {
        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        List<ImpactoRestricaoResponse.InscritoImpactado> afetados = new ArrayList<>();

        for (InscricaoEvento inscricao : inscricoes) {
            if (inscricao.isInscritoPorExcecao()) continue;

            Elegibilidade elegibilidade = elegibilidadeService.avaliar(
                    regrasHipoteticas, inscricao.getPessoa());
            if (!elegibilidade.apto()) {
                List<String> motivos = elegibilidade.impedimentos().stream()
                        .map(Impedimento::mensagem)
                        .toList();
                afetados.add(new ImpactoRestricaoResponse.InscritoImpactado(
                        inscricao.getPessoa().getId(), inscricao.getPessoa().getNome(), motivos));
            }
        }
        return afetados;
    }

    /**
     * Cancela as inscrições CONFIRMADAS de uma pessoa em eventos {@code exclusivoMembros},
     * chamada quando o vínculo dela deixa de ser MEMBRO (ver {@link
     * com.domus.api.modules.pessoa.PessoaService#atualizarMembro}).
     *
     * <p>Sem isto, alguém que perde o vínculo MEMBRO continuaria confirmado e ocupando vaga
     * num evento exclusivo para membros — a mesma inscrição que {@link #validarElegibilidade}
     * recusaria se fosse tentada de novo. Reusa {@link #cancelarInterno}, então os
     * acompanhantes são removidos junto, igual a qualquer outro cancelamento.
     *
     * @return quantas inscrições foram canceladas.
     */
    @Transactional
    public int cancelarInscricoesEmEventosExclusivos(UUID pessoaId) {
        List<InscricaoEvento> inscricoes = inscricaoRepository
                .findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(pessoaId, StatusInscricao.CONFIRMADA);

        for (InscricaoEvento inscricao : inscricoes) {
            cancelarInterno(inscricao);
        }

        if (!inscricoes.isEmpty()) {
            log.info("Inscrições canceladas por perda de vínculo MEMBRO. pessoa_id={}, canceladas={}",
                    pessoaId, inscricoes.size());
        }
        return inscricoes.size();
    }

    /** Lista de inscritos confirmados + contagem de vagas restantes. */
    @Transactional(readOnly = true)
    public ListaInscritosResponse listarInscritos(UUID eventoId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);

        // Resolve "quem inscreveu" em UMA query para a lista inteira (evita N+1): coleta os
        // ids distintos e não-nulos e busca nome+foto em lote. Ids ausentes no mapa de volta
        // (conta ou membro arquivados depois da inscrição) viram null no DTO — tratados
        // explicitamente, não escondidos atrás de um texto genérico incorreto.
        Map<UUID, RegistranteResumo> registrantes = buscarRegistrantesEmLote(inscricoes);

        List<InscritoResponse> inscritos = inscricoes.stream()
                .map(i -> InscritoResponse.from(i, registrantes.get(i.getInscritoPorUsuarioId())))
                .toList();

        long total = inscricaoRepository.contarPessoasConfirmadas(eventoId);
        Integer restantes = evento.getVagas() == null
                ? null
                : Math.max(0, evento.getVagas() - (int) total);

        return new ListaInscritosResponse(total, evento.getVagas(), restantes, inscritos);
    }

    /**
     * Lista de participantes visível a QUALQUER MEMBRO — versão reduzida de
     * {@link #listarInscritos}, sem telefone de convidado, sem "quem inscreveu quem" e sem
     * data da inscrição (ver Javadoc de {@link ParticipanteResponse}).
     */
    @Transactional(readOnly = true)
    public List<ParticipanteResponse> listarParticipantes(UUID eventoId, UUID igrejaId) {
        eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        return inscricaoRepository.listarPorEvento(eventoId)
                .stream().map(ParticipanteResponse::from).toList();
    }

    /**
     * Busca nome+foto de quem inscreveu para toda a lista, numa única query (ou nenhuma,
     * se ninguém foi inscrito por terceiro). {@code Map.of()} do {@code Collectors.toMap}
     * já garante ids únicos porque a origem é uma coluna de PK.
     */
    private Map<UUID, RegistranteResumo> buscarRegistrantesEmLote(List<InscricaoEvento> inscricoes) {
        List<UUID> ids = inscricoes.stream()
                .map(InscricaoEvento::getInscritoPorUsuarioId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        // HashMap (não Map.of()): .get(null) é uma consulta legítima e frequente aqui —
        // toda auto-inscrição tem inscritoPorUsuarioId nulo, e Map.of() lança NPE em
        // chave nula.
        Map<UUID, RegistranteResumo> mapa = new HashMap<>();
        if (ids.isEmpty()) {
            return mapa;
        }

        for (RegistranteResumo r : usuarioRepository.buscarRegistrantes(ids)) {
            mapa.put(r.usuarioId(), r);
        }
        return mapa;
    }

    private InscricaoEvento buscarInscricao(UUID id, UUID igrejaId) {
        return inscricaoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
    }
}
