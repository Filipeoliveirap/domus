package com.domus.api.modules.inicio;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.inicio.dto.EventoResumoDTO;
import com.domus.api.modules.inicio.dto.InicioResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InicioService {

    private static final int LIMITE_EVENTOS = 5;

    private final PessoaRepository pessoaRepository;
    private final EventoRepository eventoRepository;
    private final FamiliaIgrejaService familiaIgrejaService;

    @Transactional(readOnly = true)
    public InicioResponse carregar(UUID igrejaId) {
        int mes = LocalDate.now().getMonthValue();

        List<InicioResponse.Aniversariante> aniversariantes =
                pessoaRepository.aniversariantesDoMes(igrejaId, mes).stream()
                        .map(m -> new InicioResponse.Aniversariante(
                                m.getId(), m.getNome(), m.getDataNascimento().getDayOfMonth(),
                                m.getFoto() != null ? m.getFoto().getId() : null))
                        .toList();

        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        List<EventoResumoDTO> proximos =
                eventoRepository.proximosDaFamilia(igrejaId, idsFamilia, LocalDateTime.now(),
                                PageRequest.of(0, LIMITE_EVENTOS))
                        .stream().map(EventoResumoDTO::from).toList();

        return new InicioResponse(aniversariantes, proximos);
    }
}
