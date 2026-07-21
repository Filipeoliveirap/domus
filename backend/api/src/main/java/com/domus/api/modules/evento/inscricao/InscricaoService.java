package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
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

import java.time.LocalDateTime;
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
     * <p>O evento é buscado COM LOCK: a contagem de vagas e o insert precisam ser atômicos,
     * senão duas inscrições simultâneas na última vaga passam as duas.
     */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID membroId,
                                            UUID inscritoPorOuNull, UUID igrejaId) {
        Evento evento = eventoRepository.buscarComLock(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Membro membro = membroRepository.findByIdAndIgrejaId(membroId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        validarInscricaoHabilitada(evento);
        validarEventoAberto(evento);
        validarElegibilidade(evento, membro);

        InscricaoEvento inscricao = inscricaoRepository
                .findByEventoIdAndMembroId(eventoId, membroId)
                .orElse(null);

        if (inscricao != null && inscricao.estaConfirmada()) {
            throw new BusinessException("JA_INSCRITO", "Esta pessoa já está inscrita neste evento.");
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
     * Inscreve vários membros de uma vez (modal de seleção múltipla).
     *
     * <p><b>Tudo ou nada, por decisão:</b> se um membro falhar (ex.: já inscrito), a transação
     * inteira volta atrás e nenhum é inscrito. O erro nomeia a pessoa, quem escolheu desmarca
     * e reenvia. Resultado parcial exigiria DTO e tela próprios para um caso que ainda não
     * sabemos se acontece.
     */
    @Transactional
    public void inscreverMembros(UUID eventoId, List<UUID> membroIds,
                                 UUID inscritoPorUsuarioId, UUID igrejaId) {
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
     * Checada ANTES de "aberto" e elegibilidade: é um interruptor de feature, não uma regra
     * de negócio sobre QUEM pode se inscrever. Se o evento nem organiza inscrição, não faz
     * sentido gastar as próximas validações (data, exclusividade) para chegar num "não" que já
     * era certo desde o cadastro do evento.
     */
    private void validarInscricaoHabilitada(Evento evento) {
        if (!evento.isRequerInscricao()) {
            throw new BusinessException("INSCRICAO_NAO_HABILITADA",
                    "Este evento não abre inscrição. Fale com a administração da igreja.");
        }
    }

    private void validarEventoAberto(Evento evento) {
        if (evento.getInicioEm().isBefore(LocalDateTime.now())) {
            throw new BusinessException("EVENTO_ENCERRADO",
                    "Este evento já aconteceu. Não é mais possível se inscrever.");
        }
    }

    private void validarElegibilidade(Evento evento, Membro membro) {
        if (evento.isExclusivoMembros() && membro.getStatus() == StatusMembro.VISITANTE) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros da igreja.");
        }
        if (evento.isExclusivoBatizados() && !membro.isBatizado()) {
            throw new BusinessException("EXCLUSIVO_BATIZADOS",
                    "Este evento é exclusivo para membros batizados.");
        }
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
        validarInscricaoHabilitada(inscricao.getEvento());

        if (inscricao.getEvento().isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(inscricao.getEvento());

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
     */
    @Transactional
    public void cancelar(UUID inscricaoId, UUID usuarioId, UUID meuMembroId,
                         String role, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        boolean ehAdmin = "ADMIN_IGREJA".equals(role) || "LIDER".equals(role);
        boolean souEu = inscricao.getMembro().getId().equals(meuMembroId);

        if (!ehAdmin && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode cancelar a inscrição de outra pessoa. "
                    + "Peça a ela ou a um líder da igreja.");
        }

        // Os convidados vão embora com a inscrição, e NÃO voltam numa reinscrição.
        // Sem isto, quem cancelou porque o convidado desistiu o veria reaparecer em
        // silêncio ao se reinscrever — ocupando vaga de novo. Quem quiser levá-lo de
        // novo cadastra de novo; é o passo consciente que o silêncio não teria.
        int convidados = inscricao.getAcompanhantes().size();
        inscricao.getAcompanhantes().clear();   // orphanRemoval = true apaga as linhas

        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
        log.info("Inscrição cancelada. id={}, convidados_removidos={}, por_usuario={}, igreja_id={}",
                inscricaoId, convidados, usuarioId, igrejaId);
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
