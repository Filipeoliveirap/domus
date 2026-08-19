package com.domus.api.modules.termos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermoAceiteServiceTest {

    TermoAceiteRepository termoAceiteRepository;
    TermoAceiteService service;
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        termoAceiteRepository = mock(TermoAceiteRepository.class);
        service = new TermoAceiteService(termoAceiteRepository);
    }

    @Test
    void exigirAceiteNaoLancaQuandoTrue() {
        service.exigirAceite(true);
    }

    @Test
    void exigirAceiteLancaQuandoFalse() {
        assertThatThrownBy(() -> service.exigirAceite(false))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("Termos");
    }

    @Test
    void exigirAceiteLancaQuandoNull() {
        assertThatThrownBy(() -> service.exigirAceite(null))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);
    }

    @Test
    void registrarAceiteSalvaAsDuasLinhas() {
        service.registrarAceite(usuarioId, "203.0.113.9");

        verify(termoAceiteRepository, times(2)).save(any(TermoAceite.class));
    }

    @Test
    void registrarAceiteGravaOsDoisTiposComVersaoEIpCorretos() {
        var capturado = org.mockito.ArgumentCaptor.forClass(TermoAceite.class);

        service.registrarAceite(usuarioId, "203.0.113.9");

        verify(termoAceiteRepository, times(2)).save(capturado.capture());
        var tipos = capturado.getAllValues().stream().map(TermoAceite::getTipo).toList();
        assertThat(tipos).containsExactlyInAnyOrder(TipoTermo.TERMOS_DE_USO, TipoTermo.POLITICA_PRIVACIDADE);
        capturado.getAllValues().forEach(t -> {
            assertThat(t.getVersao()).isEqualTo(TermosConstantes.VERSAO_ATUAL);
            assertThat(t.getIp()).isEqualTo("203.0.113.9");
            assertThat(t.getUsuario().getId()).isEqualTo(usuarioId);
        });
    }

    @Test
    void precisaAceitarFalseQuandoAmbosOsTiposBatemComVersaoAtual() {
        when(termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL))
                .thenReturn(2L);

        assertThat(service.precisaAceitar(usuarioId)).isFalse();
    }

    @Test
    void precisaAceitarTrueQuandoNenhumRegistro() {
        when(termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL))
                .thenReturn(0L);

        assertThat(service.precisaAceitar(usuarioId)).isTrue();
    }

    @Test
    void precisaAceitarTrueQuandoSoUmTipoBateComVersaoAtual() {
        when(termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL))
                .thenReturn(1L);

        assertThat(service.precisaAceitar(usuarioId)).isTrue();
    }

    @Test
    void dataUltimoAceiteDelegaParaORepositorio() {
        LocalDateTime esperado = LocalDateTime.now();
        when(termoAceiteRepository.buscarUltimoAceite(usuarioId)).thenReturn(esperado);

        assertThat(service.dataUltimoAceite(usuarioId)).isEqualTo(esperado);
    }
}
