package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse;
import com.domus.api.modules.igreja.familia.consolidado.ConsolidadoProjections.MembrosPorIgreja;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Indexar array por ordinal() de Vinculo é bomba-relógio pro dia de um 3º vínculo; agregação usa EnumMap<Vinculo, Long> como chave. */
class ConsolidadoServiceTest {

    ConsolidadoRepository repository;
    IgrejaRepository igrejaRepository;
    FamiliaIgrejaService familiaService;
    ConsolidadoService service;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(ConsolidadoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        familiaService = mock(FamiliaIgrejaService.class);
        service = new ConsolidadoService(repository, igrejaRepository, familiaService);

        when(familiaService.idsDaFamilia(igrejaId)).thenReturn(List.of(igrejaId));

        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        igreja.setNome("Igreja Central");
        when(igrejaRepository.findAllById(List.of(igrejaId))).thenReturn(List.of(igreja));

        when(repository.contarEventos(any(), any())).thenReturn(List.of());
        when(repository.agregarFinanceiro(any(), any(), any())).thenReturn(List.of());
    }

    private MembrosPorIgreja linha(UUID igrejaId, String vinculo, long total) {
        MembrosPorIgreja l = mock(MembrosPorIgreja.class);
        when(l.getIgrejaId()).thenReturn(igrejaId);
        when(l.getVinculo()).thenReturn(vinculo);
        when(l.getTotal()).thenReturn(total);
        return l;
    }

    @Test
    void separaMembrosECongregantesECalculaOTotalCorretamente() {
        MembrosPorIgreja membros3 = linha(igrejaId, "MEMBRO", 3L);
        MembrosPorIgreja congregantes2 = linha(igrejaId, "CONGREGANTE", 2L);
        when(repository.contarMembros(List.of(igrejaId)))
                .thenReturn(List.of(membros3, congregantes2));

        ConsolidadoResponse resposta = service.gerar(igrejaId, LocalDate.now(), LocalDate.now());

        assertThat(resposta.porIgreja()).hasSize(1);
        ConsolidadoResponse.Pessoas pessoas = resposta.porIgreja().get(0).pessoas();
        assertThat(pessoas.membros()).isEqualTo(3);
        assertThat(pessoas.congregantes()).isEqualTo(2);
        assertThat(pessoas.total()).isEqualTo(5);

        // Totais da família batem com a única igreja da lista.
        assertThat(resposta.familia().pessoas().membros()).isEqualTo(3);
        assertThat(resposta.familia().pessoas().congregantes()).isEqualTo(2);
        assertThat(resposta.familia().pessoas().total()).isEqualTo(5);
    }
}
