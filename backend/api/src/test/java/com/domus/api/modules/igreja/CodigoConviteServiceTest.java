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

        when(igrejaRepository.buscarComLock(matrizId)).thenReturn(Optional.of(matriz));
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
        matriz.setPlano(PlanoAssinatura.PRO); // permite 2

        when(igrejaRepository.buscarComLock(matrizId)).thenReturn(Optional.of(matriz));
        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(2L);

        assertThatThrownBy(() -> codigoConviteService.gerarCodigo(matriz))
            .isInstanceOf(PlanoLimiteExcedidoException.class)
            .hasMessageContaining("permite no máximo 2 congregações");
    }

    @Test
    void deveValidarEConsumirCodigoValido() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setPlano(PlanoAssinatura.PRO);
        matriz.setCodigoVinculo("DOMUS-A1B2C3");

        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo("DOMUS-A1B2C3");

        Igreja filha = new Igreja();
        filha.setId(UUID.randomUUID());

        when(codigoConviteRepository.findByCodigo("DOMUS-A1B2C3")).thenReturn(Optional.of(convite));
        when(igrejaRepository.buscarComLock(matrizId)).thenReturn(Optional.of(matriz));
        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(1L);
        when(codigoConviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CodigoConviteCongregacao consumido = codigoConviteService.validarEConsumirCodigo("DOMUS-A1B2C3", filha);

        assertThat(consumido.getUsadoEm()).isNotNull();
        assertThat(filha.getIgrejaMae()).isEqualTo(matriz);
        assertThat(filha.getStatusAssinatura()).isEqualTo(StatusAssinatura.ATIVA);
    }

    @Test
    void deveConsultarCodigoInexistenteRetornandoExpirado() {
        when(codigoConviteRepository.findByCodigo("DOMUS-INVALIDO")).thenReturn(Optional.empty());
        when(igrejaRepository.findByCodigoVinculo("DOMUS-INVALIDO")).thenReturn(Optional.empty());

        var response = codigoConviteService.consultarCodigo("DOMUS-INVALIDO");

        assertThat(response.estado()).isEqualTo("EXPIRADO");
    }

    @Test
    void devePermitirReutilizarCodigoEnquantoHouverVagas() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setNome("Igreja Matriz");
        matriz.setPlano(PlanoAssinatura.PRO); // limite 2
        matriz.setCodigoVinculo("DOMUS-ATIVO1");

        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo("DOMUS-ATIVO1");
        convite.setUsadoEm(java.time.LocalDateTime.now()); // Já usado por uma congregação antes

        when(codigoConviteRepository.findByCodigo("DOMUS-ATIVO1")).thenReturn(Optional.of(convite));
        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(1L); // 1 de 2: ainda tem vaga!

        var response = codigoConviteService.consultarCodigo("DOMUS-ATIVO1");

        assertThat(response.estado()).isEqualTo("VALIDO");
        assertThat(response.matrizNome()).isEqualTo("Igreja Matriz");
    }

    @Test
    void deveConsultarCodigoMatrizComLimiteExcedido() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setNome("Matriz Lotada");
        matriz.setPlano(PlanoAssinatura.PRO); // limite 2
        matriz.setCodigoVinculo("DOMUS-LOTADO");
        matriz.setCongregacaoNomePlural("Redes");

        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo("DOMUS-LOTADO");

        when(codigoConviteRepository.findByCodigo("DOMUS-LOTADO")).thenReturn(Optional.of(convite));
        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(2L);

        var response = codigoConviteService.consultarCodigo("DOMUS-LOTADO");

        assertThat(response.estado()).isEqualTo("MATRIZ_LIMITE_EXCEDIDO");
        assertThat(response.matrizNome()).isEqualTo("Matriz Lotada");
        assertThat(response.rotuloCongregacaoPlural()).isEqualTo("Redes");
    }

    @Test
    void deveRecusarConsumirCodigoJaUtilizadoOuInexistente() {
        when(codigoConviteRepository.findByCodigo("DOMUS-INVALIDO")).thenReturn(Optional.empty());
        when(igrejaRepository.findByCodigoVinculo("DOMUS-INVALIDO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> codigoConviteService.validarEConsumirCodigo("DOMUS-INVALIDO", new Igreja()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inválido ou expirado");
    }

    @Test
    void deveRecusarConsumirCodigoQuandoMatrizAtingirLimite() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = new Igreja();
        matriz.setId(matrizId);
        matriz.setPlano(PlanoAssinatura.PRO); // limite 2
        matriz.setCodigoVinculo("DOMUS-LOTADO");

        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo("DOMUS-LOTADO");

        when(codigoConviteRepository.findByCodigo("DOMUS-LOTADO")).thenReturn(Optional.of(convite));
        when(igrejaRepository.buscarComLock(matrizId)).thenReturn(Optional.of(matriz));
        when(igrejaRepository.countByIgrejaMaeId(matrizId)).thenReturn(2L);

        assertThatThrownBy(() -> codigoConviteService.validarEConsumirCodigo("DOMUS-LOTADO", new Igreja()))
                .isInstanceOf(PlanoLimiteExcedidoException.class)
                .hasMessageContaining("atingiu o limite de congregações");
    }
}
