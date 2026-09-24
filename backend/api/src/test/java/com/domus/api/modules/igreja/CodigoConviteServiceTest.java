package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodigoConviteServiceTest {

    @Mock
    private CodigoConviteRepository codigoConviteRepository;

    @Mock
    private IgrejaRepository igrejaRepository;

    @InjectMocks
    private CodigoConviteService codigoConviteService;

    @Test
    void deveGerarCodigoConviteQuandoHouverVagasNoPlano() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setPlano(PlanoAssinatura.PRO); // permite 3

        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(1L);
        when(codigoConviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GerarCodigoConviteResponse response = codigoConviteService.gerarCodigo(matriz);

        assertThat(response.codigo()).startsWith("DOMUS-");
    }

    @Test
    void deveLancarExcecaoQuandoMatrizAtingirLimiteDeCongregacoes() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setPlano(PlanoAssinatura.PRO); // permite 3

        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(3L);

        assertThatThrownBy(() -> codigoConviteService.gerarCodigo(matriz))
            .isInstanceOf(PlanoLimiteExcedidoException.class)
            .hasMessageContaining("permite no máximo 3 congregações");
    }

    @Test
    void deveValidarEConsumirCodigoValido() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setPlano(PlanoAssinatura.PRO);

        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo("DOMUS-A1B2C3");

        Igreja filha = new Igreja();
        filha.setId(UUID.randomUUID());

        when(codigoConviteRepository.findByCodigoAndUsadoEmIsNull("DOMUS-A1B2C3")).thenReturn(Optional.of(convite));
        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(1L);
        when(codigoConviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CodigoConviteCongregacao consumido = codigoConviteService.validarEConsumirCodigo("DOMUS-A1B2C3", filha);

        assertThat(consumido.getUsadoEm()).isNotNull();
        assertThat(filha.getIgrejaMae()).isEqualTo(matriz);
        assertThat(filha.getStatusAssinatura()).isEqualTo(StatusAssinatura.ATIVA);
    }
}
