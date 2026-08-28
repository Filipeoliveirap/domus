package com.domus.api.shared.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissoesTest {

    @Test
    void gerenciarInscricoes_valeParaAdminELider_naoParaComum() {
        assertThat(Permissoes.podeGerenciarInscricoes("ADMIN_IGREJA")).isTrue();
        assertThat(Permissoes.podeGerenciarInscricoes("LIDER")).isTrue();
        assertThat(Permissoes.podeGerenciarInscricoes("ACESSO_COMUM")).isFalse();
    }

    @Test
    void dadosSensiveisDePessoa_soAdmin() {
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("ADMIN_IGREJA")).isTrue();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("LIDER")).isFalse();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("ACESSO_COMUM")).isFalse();
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

    @Test
    void secretarioEstendeGerenciarPessoas() {
        assertThat(Permissoes.podeGerenciarPessoas("ACESSO_COMUM", Set.of("SECRETARIO"))).isTrue();
        assertThat(Permissoes.podeGerenciarPessoas("ACESSO_COMUM", Set.of("TESOUREIRO"))).isFalse();
        assertThat(Permissoes.podeGerenciarPessoas("ACESSO_COMUM", Set.of())).isFalse();
        assertThat(Permissoes.podeGerenciarPessoas("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void secretarioEstendeDadosSensiveisDePessoa() {
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("LIDER", Set.of("SECRETARIO"))).isTrue();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("LIDER", Set.of())).isFalse();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void secretarioEstendeGerenciarVisitantes() {
        assertThat(Permissoes.podeGerenciarVisitantes("LIDER", Set.of("SECRETARIO"))).isTrue();
        assertThat(Permissoes.podeGerenciarVisitantes("LIDER", Set.of())).isFalse();
        assertThat(Permissoes.podeGerenciarVisitantes("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void tesoureiroEstendeVerFinanceiro() {
        assertThat(Permissoes.podeVerFinanceiro("LIDER", Set.of("TESOUREIRO"))).isTrue();
        assertThat(Permissoes.podeVerFinanceiro("LIDER", Set.of("SECRETARIO"))).isFalse();
        assertThat(Permissoes.podeVerFinanceiro("LIDER", Set.of())).isFalse();
        assertThat(Permissoes.podeVerFinanceiro("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void tesoureiroEstendeUsuariosEFinanceiroNaBuscaGlobal() {
        assertThat(Permissoes.podeVerUsuariosEFinanceiroNaBuscaGlobal("ACESSO_COMUM", Set.of("TESOUREIRO"))).isTrue();
        assertThat(Permissoes.podeVerUsuariosEFinanceiroNaBuscaGlobal("ACESSO_COMUM", Set.of())).isFalse();
        assertThat(Permissoes.podeVerUsuariosEFinanceiroNaBuscaGlobal("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void secretarioEstendeGerenciarCelulas() {
        assertThat(Permissoes.podeGerenciarCelulas("LIDER", Set.of("SECRETARIO"))).isTrue();
        assertThat(Permissoes.podeGerenciarCelulas("LIDER", Set.of("TESOUREIRO"))).isFalse();
        assertThat(Permissoes.podeGerenciarCelulas("LIDER", Set.of())).isFalse();
        assertThat(Permissoes.podeGerenciarCelulas("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void secretarioEstendeGerenciarCadastroMinisterios() {
        assertThat(Permissoes.podeGerenciarCadastroMinisterios("LIDER", Set.of("SECRETARIO"))).isTrue();
        assertThat(Permissoes.podeGerenciarCadastroMinisterios("LIDER", Set.of("TESOUREIRO"))).isFalse();
        assertThat(Permissoes.podeGerenciarCadastroMinisterios("LIDER", Set.of())).isFalse();
        assertThat(Permissoes.podeGerenciarCadastroMinisterios("ADMIN_IGREJA", Set.of())).isTrue();
    }

    @Test
    void capacidadeExtraNulaNaoQuebraENaoLibera() {
        assertThat(Permissoes.podeGerenciarPessoas("ACESSO_COMUM", null)).isFalse();
        assertThat(Permissoes.podeVerFinanceiro("ACESSO_COMUM", null)).isFalse();
    }
}
