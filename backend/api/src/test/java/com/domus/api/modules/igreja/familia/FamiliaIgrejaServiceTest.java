package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.exception.AcessoNegadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * O teste mais importante da feature: um furo aqui vaza financeiro entre igrejas.
 *
 * <p>Cenário das duas famílias:
 * família A = maeA + filhaA1, filhaA2; família B = maeB + filhaB1.
 */
class FamiliaIgrejaServiceTest {

    IgrejaRepository igrejaRepository;
    FamiliaIgrejaService service;

    UUID maeA = UUID.randomUUID();
    UUID filhaA1 = UUID.randomUUID();
    UUID filhaA2 = UUID.randomUUID();
    UUID maeB = UUID.randomUUID();
    UUID filhaB1 = UUID.randomUUID();
    UUID independente = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        igrejaRepository = mock(IgrejaRepository.class);
        service = new FamiliaIgrejaService(igrejaRepository);

        when(igrejaRepository.existsByIgrejaMaeId(maeA)).thenReturn(true);
        when(igrejaRepository.existsByIgrejaMaeId(maeB)).thenReturn(true);
        when(igrejaRepository.existsByIgrejaMaeId(filhaA1)).thenReturn(false);
        when(igrejaRepository.existsByIgrejaMaeId(independente)).thenReturn(false);

        when(igrejaRepository.buscarIdsDasFilhas(maeA)).thenReturn(List.of(filhaA1, filhaA2));
        when(igrejaRepository.buscarIdsDasFilhas(maeB)).thenReturn(List.of(filhaB1));
        when(igrejaRepository.buscarIdsDasFilhas(filhaA1)).thenReturn(List.of());
        when(igrejaRepository.buscarIdsDasFilhas(independente)).thenReturn(List.of());

        stubIgreja(maeA, null);
        stubIgreja(maeB, null);
        stubIgreja(filhaA1, maeA);
        stubIgreja(filhaA2, maeA);
        stubIgreja(filhaB1, maeB);
        stubIgreja(independente, null);
    }

    private void stubIgreja(UUID id, UUID maeId) {
        Igreja igreja = new Igreja();
        igreja.setId(id);
        if (maeId != null) {
            Igreja mae = new Igreja();
            mae.setId(maeId);
            igreja.setIgrejaMae(mae);
        }
        when(igrejaRepository.findById(id)).thenReturn(Optional.of(igreja));
    }

    // ---------- IDOR: o risco número um ----------

    @Test
    void maeDaFamiliaANaoAcessaIgrejaDaFamiliaB() {
        assertThatThrownBy(() -> service.validarAcesso(maeA, filhaB1))
                .isInstanceOf(AcessoNegadoException.class);

        assertThatThrownBy(() -> service.validarAcesso(maeA, maeB))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void filhaNaoEnxergaAMae() {
        assertThatThrownBy(() -> service.validarAcesso(filhaA1, maeA))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void filhaNaoEnxergaAIrma() {
        assertThatThrownBy(() -> service.validarAcesso(filhaA1, filhaA2))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void maeAcessaAPropriaFilha() {
        assertThatCode(() -> service.validarAcesso(maeA, filhaA1)).doesNotThrowAnyException();
    }

    @Test
    void qualquerIgrejaAcessaASiMesma() {
        assertThatCode(() -> service.validarAcesso(filhaA1, filhaA1)).doesNotThrowAnyException();
        assertThatCode(() -> service.validarAcesso(independente, independente)).doesNotThrowAnyException();
    }

    // ---------- resolverEscopo: comportamento de hoje intacto ----------

    @Test
    void escopoSemIgrejaIdCaiNaPropriaIgreja() {
        assertThat(service.resolverEscopo(maeA, null)).isEqualTo(maeA);
        assertThat(service.resolverEscopo(independente, null)).isEqualTo(independente);
    }

    @Test
    void escopoComIgrejaIdDaFamiliaPassa() {
        assertThat(service.resolverEscopo(maeA, filhaA1)).isEqualTo(filhaA1);
    }

    @Test
    void escopoComIgrejaIdDeOutraFamiliaEhRecusado() {
        assertThatThrownBy(() -> service.resolverEscopo(maeA, filhaB1))
                .isInstanceOf(AcessoNegadoException.class);
    }

    // ---------- idsDaFamilia ----------

    @Test
    void familiaDaMaeEhElaMaisAsFilhas() {
        assertThat(service.idsDaFamilia(maeA)).containsExactlyInAnyOrder(maeA, filhaA1, filhaA2);
    }

    @Test
    void familiaDaFilhaEhSoElaMesma() {
        assertThat(service.idsDaFamilia(filhaA1)).containsExactly(filhaA1);
    }

    @Test
    void familiaDaIndependenteEhSoElaMesma() {
        assertThat(service.idsDaFamilia(independente)).containsExactly(independente);
    }

    @Test
    void maeEhQuemTemFilhaNaoQuemTemCodigo() {
        assertThat(service.ehMae(maeA)).isTrue();
        assertThat(service.ehMae(independente)).isFalse();
    }
}
