package com.domus.api.shared.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PermissoesTest {

    @Test
    void gerenciarInscricoes_valeParaAdminELider_naoParaComum() {
        assertThat(Permissoes.podeGerenciarInscricoes("ADMIN_IGREJA")).isTrue();
        assertThat(Permissoes.podeGerenciarInscricoes("LIDER")).isTrue();
        assertThat(Permissoes.podeGerenciarInscricoes("MEMBRO")).isFalse();
    }

    @Test
    void dadosSensiveisDePessoa_soAdmin() {
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("ADMIN_IGREJA")).isTrue();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("LIDER")).isFalse();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("MEMBRO")).isFalse();
    }

    @Test
    void roleDesconhecidaOuNulaNaoRecebeNada() {
        // Fail-closed: perfil que não existe (token adulterado, role removida do banco)
        // não pode cair no ramo permissivo por acidente.
        for (String r : new String[]{null, "", "ROOT", "admin_igreja"}) {
            assertThat(Permissoes.podeGerenciarInscricoes(r)).isFalse();
            assertThat(Permissoes.podeVerDadosSensiveisDePessoa(r)).isFalse();
            assertThat(Permissoes.podeGerenciarEventos(r)).isFalse();
        }
    }
}
