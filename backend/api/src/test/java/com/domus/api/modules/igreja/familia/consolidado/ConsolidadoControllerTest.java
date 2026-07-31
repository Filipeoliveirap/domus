package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.shared.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConsolidadoControllerTest {

    ConsolidadoService service;
    UsuarioAutenticado usuarioAutenticado;
    FamiliaIgrejaService familiaIgrejaService;
    ConsolidadoController controller;

    UUID igrejaId = UUID.randomUUID();
    LocalDate inicio = LocalDate.of(2026, 1, 1);
    LocalDate fim = LocalDate.of(2026, 1, 31);

    @BeforeEach
    void setup() {
        service = mock(ConsolidadoService.class);
        usuarioAutenticado = mock(UsuarioAutenticado.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        controller = new ConsolidadoController(service, usuarioAutenticado, familiaIgrejaService);

        when(usuarioAutenticado.getIgrejaId()).thenReturn(igrejaId);
    }

    @Test
    void acessoComumSemCapacidadeExtraERecusado() {
        when(usuarioAutenticado.getRole()).thenReturn("ACESSO_COMUM");
        when(usuarioAutenticado.getCapacidadesExtras()).thenReturn(Set.of());

        assertThatThrownBy(() -> controller.consolidado(inicio, fim))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).gerar(any(), any(), any());
    }

    @Test
    void liderSemCapacidadeExtraERecusado() {
        when(usuarioAutenticado.getRole()).thenReturn("LIDER");
        when(usuarioAutenticado.getCapacidadesExtras()).thenReturn(Set.of());

        assertThatThrownBy(() -> controller.consolidado(inicio, fim))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).gerar(any(), any(), any());
    }

    @Test
    void tesoureiroDaFilhaERecusadoMesmoTendoCapacidadeFinanceira() {
        when(usuarioAutenticado.getRole()).thenReturn("ACESSO_COMUM");
        when(usuarioAutenticado.getCapacidadesExtras()).thenReturn(Set.of("TESOUREIRO"));
        when(familiaIgrejaService.ehFilha(igrejaId)).thenReturn(true);

        assertThatThrownBy(() -> controller.consolidado(inicio, fim))
                .isInstanceOf(AccessDeniedException.class);
        verify(service, never()).gerar(any(), any(), any());
    }

    @Test
    void adminDaSedeConsultaOConsolidado() {
        when(usuarioAutenticado.getRole()).thenReturn("ADMIN_IGREJA");
        when(usuarioAutenticado.getCapacidadesExtras()).thenReturn(Set.of());
        when(familiaIgrejaService.ehFilha(igrejaId)).thenReturn(false);

        controller.consolidado(inicio, fim);

        verify(service).gerar(igrejaId, inicio, fim);
    }

    @Test
    void tesoureiroDaSedeConsultaOConsolidado() {
        when(usuarioAutenticado.getRole()).thenReturn("ACESSO_COMUM");
        when(usuarioAutenticado.getCapacidadesExtras()).thenReturn(Set.of("TESOUREIRO"));
        when(familiaIgrejaService.ehFilha(igrejaId)).thenReturn(false);

        controller.consolidado(inicio, fim);

        verify(service).gerar(igrejaId, inicio, fim);
    }
}
