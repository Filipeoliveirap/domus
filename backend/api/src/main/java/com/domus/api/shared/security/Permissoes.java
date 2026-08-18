package com.domus.api.shared.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Compare capacidade, não identidade — string comparada em vários services diverge sem quebrar compilação nem teste. */
public final class Permissoes {

    private Permissoes() {}

    private static final Set<Perfil> GESTORES = EnumSet.of(Perfil.ADMIN_IGREJA, Perfil.LIDER);
    private static final Set<Perfil> SO_ADMIN = EnumSet.of(Perfil.ADMIN_IGREJA);

    private static boolean tem(String nomeRole, Set<Perfil> permitidos) {
        Perfil role = Perfil.deNomeOuNull(nomeRole);
        return role != null && permitidos.contains(role);
    }

    private static boolean temCapacidade(String nomeRole, Set<String> capacidadesExtras, Set<Perfil> permitidos, String capacidade) {
        return tem(nomeRole, permitidos) || (capacidadesExtras != null && capacidadesExtras.contains(capacidade));
    }

    /** Cancelar inscrição de outra pessoa, inscrever terceiros, remover convidado alheio. */
    public static boolean podeGerenciarInscricoes(String role) { return tem(role, GESTORES); }

    /** Lista completa de inscritos — inclui telefone de convidado e quem inscreveu quem. */
    public static boolean podeVerListaCompletaDeInscritos(String role) { return tem(role, GESTORES); }

    /** Endereço e observações de uma pessoa. */
    public static boolean podeVerDadosSensiveisDePessoa(String role) { return tem(role, SO_ADMIN); }
    public static boolean podeVerDadosSensiveisDePessoa(String role, Set<String> capacidadesExtras) {
        return temCapacidade(role, capacidadesExtras, SO_ADMIN, "SECRETARIO");
    }

    /** Cadastrar, editar e arquivar pessoas. */
    public static boolean podeGerenciarPessoas(String role) { return tem(role, SO_ADMIN); }
    public static boolean podeGerenciarPessoas(String role, Set<String> capacidadesExtras) {
        return temCapacidade(role, capacidadesExtras, SO_ADMIN, "SECRETARIO");
    }

    /** Criar, editar e arquivar eventos. */
    public static boolean podeGerenciarEventos(String role) { return tem(role, GESTORES); }

    /** Movimentações, categorias, relatórios e dashboard. */
    public static boolean podeVerFinanceiro(String role) { return tem(role, SO_ADMIN); }
    public static boolean podeVerFinanceiro(String role, Set<String> capacidadesExtras) {
        return temCapacidade(role, capacidadesExtras, SO_ADMIN, "TESOUREIRO");
    }

    /** Busca global: inclui usuários e dados financeiros nos resultados. */
    public static boolean podeVerUsuariosEFinanceiroNaBuscaGlobal(String role) { return tem(role, SO_ADMIN); }
    public static boolean podeVerUsuariosEFinanceiroNaBuscaGlobal(String role, Set<String> capacidadesExtras) {
        return temCapacidade(role, capacidadesExtras, SO_ADMIN, "TESOUREIRO");
    }

    /** Criar, renomear, arquivar ministério e promover/rebaixar líder. */
    public static boolean podeGerenciarCadastroMinisterios(String role) { return tem(role, SO_ADMIN); }

    /** Cadastrar, editar, apagar e gerenciar toggles de visitantes. */
    public static boolean podeGerenciarVisitantes(String role) { return tem(role, SO_ADMIN); }
    public static boolean podeGerenciarVisitantes(String role, Set<String> capacidadesExtras) {
        return temCapacidade(role, capacidadesExtras, SO_ADMIN, "SECRETARIO");
    }

    /** Criar, arquivar células e promover líderes de célula. */
    public static boolean podeGerenciarCelulas(String role) { return tem(role, SO_ADMIN); }
}
