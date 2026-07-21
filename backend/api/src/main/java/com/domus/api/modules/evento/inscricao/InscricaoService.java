package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InscricaoService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final AcompanhanteRepository acompanhanteRepository;
    private final MembroRepository membroRepository;

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
}
