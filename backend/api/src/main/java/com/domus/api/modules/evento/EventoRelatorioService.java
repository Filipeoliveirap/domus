package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.RelatorioEventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Agregações de presença/engajamento — relatório individual (por evento) e geral (entre
 * eventos, ver Task 8). Arquivo separado de {@link EventoService}: aquele é CRUD de evento,
 * este é leitura agregada; razões de mudar diferentes.
 */
@Service
@RequiredArgsConstructor
public class EventoRelatorioService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PessoaRepository pessoaRepository;

    /**
     * Relatório de UM evento: inscritos sempre aparecem; comparecimento e
     * {@code percentualIgreja} só quando {@code evento.controlaPresenca=true} — {@code null}
     * explícito no contrário, para a seção sumir inteira no front (nunca aparecer zerada).
     */
    @Transactional(readOnly = true)
    public RelatorioEventoResponse relatorioIndividual(UUID eventoId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        long pessoasInscritas = inscricaoRepository.countPessoasInscritas(eventoId);
        long convidadosInscritos = inscricaoRepository.countConvidadosInscritos(eventoId);
        var inscritos = new RelatorioEventoResponse.Inscritos(pessoasInscritas, convidadosInscritos);

        if (!evento.isControlaPresenca()) {
            return new RelatorioEventoResponse(inscritos, null, null);
        }

        long pessoasCompareceram = inscricaoRepository.countPessoasCompareceram(eventoId);
        long convidadosCompareceram = inscricaoRepository.countConvidadosCompareceram(eventoId);
        var compareceram = new RelatorioEventoResponse.Compareceram(pessoasCompareceram, convidadosCompareceram);

        // "Impacto Global": só pessoas CADASTRADAS que compareceram sobre o total de pessoas
        // ATIVAS da igreja — convidado nunca entra (nem no numerador, nem no denominador),
        // porque a base é "pessoas da igreja", não "gente que apareceu".
        long totalAtivas = pessoaRepository.countByIgrejaId(igrejaId);
        Double percentualIgreja = totalAtivas > 0
                ? arredondar((pessoasCompareceram * 100.0) / totalAtivas)
                : 0.0;

        return new RelatorioEventoResponse(inscritos, compareceram, percentualIgreja);
    }

    /** Uma casa decimal — precisão suficiente para um percentual/média de presença. */
    static double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
