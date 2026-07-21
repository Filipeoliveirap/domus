package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.SituacaoEvento;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.InscritoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ListaInscritosResponse;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ParticipanteResponse;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final MembroRepository membroRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Inscreve um membro. {@code inscritoPorOuNull} é NULL na auto-inscrição.
     *
     * <p><b>Auto-inscrição funciona em QUALQUER evento</b> — é leve, tipo uma curtida ("eu
     * vou"). {@code requerInscricao} não bloqueia mais este método; ele passou a significar
     * só "este evento organiza vagas, convidados e inscrição de terceiros" (ver
     * {@link #inscreverMembros} e {@link #adicionarAcompanhante}, que continuam checando).
     *
     * <p>O evento é buscado COM LOCK: a contagem de vagas e o insert precisam ser atômicos,
     * senão duas inscrições simultâneas na última vaga passam as duas. {@code validarVaga}
     * já não faz nada quando {@code vagas} é NULL, que é o caso comum de evento casual —
     * então a auto-inscrição neles nunca esbarra em limite de vaga.
     */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID membroId,
                                            UUID inscritoPorOuNull, UUID igrejaId) {
        Evento evento = eventoRepository.buscarComLock(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Membro membro = membroRepository.findByIdAndIgrejaId(membroId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        validarEventoAberto(evento);
        validarElegibilidade(evento, membro);

        InscricaoEvento inscricao = inscricaoRepository
                .findByEventoIdAndMembroId(eventoId, membroId)
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
            // Reaproveita a linha cancelada: o UNIQUE (evento_id, membro_id) impediria inserir
            // outra, e sem isto quem cancelasse ficaria impedido de voltar ao próprio evento.
            inscricao.setStatus(StatusInscricao.CONFIRMADA);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .membro(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(StatusInscricao.CONFIRMADA)
                    .build();
        }

        InscricaoEvento salva = inscricaoRepository.save(inscricao);
        log.info("Inscrição confirmada. evento_id={}, membro_id={}, inscrito_por={}, igreja_id={}",
                eventoId, membroId, inscritoPorOuNull, igrejaId);
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
     */
    @Transactional
    public void inscreverMembros(UUID eventoId, List<UUID> membroIds,
                                 UUID inscritoPorUsuarioId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarOrganizaInscricao(evento, "Este evento não organiza inscrição de outras pessoas.");
        validarEventoAberto(evento);

        if (!membroIds.isEmpty()) {
            List<UUID> jaInscritos = inscricaoRepository.listarMembroIdsJaInscritos(eventoId, membroIds);
            if (!jaInscritos.isEmpty()) {
                String mensagem = jaInscritos.size() == 1
                        ? "Este membro já está inscrito no evento."
                        : jaInscritos.size() + " membros já estão inscritos no evento.";
                throw new BusinessException("JA_INSCRITO", mensagem);
            }
        }

        for (UUID membroId : membroIds) {
            inscrever(eventoId, membroId, inscritoPorUsuarioId, igrejaId);
        }
    }

    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID membroId) {
        return inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId)
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

    private void validarElegibilidade(Evento evento, Membro membro) {
        // Exclusivo para membros barra quem não é membro ATIVO da igreja: VISITANTE nunca foi
        // membro, e INATIVO deixou de ser — os dois ficam de fora, não só o primeiro.
        if (evento.isExclusivoMembros()
                && (membro.getStatus() == StatusMembro.VISITANTE
                    || membro.getStatus() == StatusMembro.INATIVO)) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros da igreja.");
        }
        if (evento.isExclusivoBatizados() && !membro.isBatizado()) {
            throw new BusinessException("EXCLUSIVO_BATIZADOS",
                    "Este evento é exclusivo para membros batizados.");
        }
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
        boolean ehAdmin = "ADMIN_IGREJA".equals(role) || "LIDER".equals(role);
        boolean souODono = inscricao.getMembro().getId().equals(meuMembroId);

        if (!ehAdmin && !souODono) {
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

        boolean ehAdmin = "ADMIN_IGREJA".equals(role) || "LIDER".equals(role);
        boolean souEu = inscricao.getMembro().getId().equals(meuMembroId);

        if (!ehAdmin && !souEu) {
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
     * Ao restringir um evento (ligar {@code exclusivoMembros} e/ou {@code exclusivoBatizados}),
     * cancela automaticamente quem já estava confirmado mas não se qualifica mais — mesma regra
     * de {@link #validarElegibilidade}, e mesmo cancelamento de {@link #cancelarInterno} (leva
     * os convidados junto).
     *
     * <p>Roda mesmo quando a restrição já estava ligada antes (não só na transição de
     * false→true): é idempotente e barato — se ninguém desqualificado sobrou de uma vez
     * anterior, não cancela ninguém agora — e evita o {@link EventoService} ter que guardar e
     * comparar o estado anterior só para decidir se chama isto.
     *
     * @return quantas inscrições foram canceladas, para o chamador logar/devolver ao front.
     */
    @Transactional
    public int removerInscritosNaoElegiveis(UUID eventoId, boolean exclusivoMembros,
                                            boolean exclusivoBatizados) {
        if (!exclusivoMembros && !exclusivoBatizados) {
            return 0;
        }

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int removidos = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            Membro membro = inscricao.getMembro();
            boolean desqualificado =
                    (exclusivoMembros && (membro.getStatus() == StatusMembro.VISITANTE
                            || membro.getStatus() == StatusMembro.INATIVO))
                    || (exclusivoBatizados && !membro.isBatizado());

            if (desqualificado) {
                cancelarInterno(inscricao);
                removidos++;
            }
        }

        if (removidos > 0) {
            log.info("Inscrições removidas por restrição de evento. evento_id={}, removidos={}",
                    eventoId, removidos);
        }
        return removidos;
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
