package com.domus.api.shared.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Política de autorização, num lugar só.
 *
 * <p><b>Por que existe:</b> antes disto o código perguntava a IDENTIDADE
 * ({@code "ADMIN_IGREJA".equals(role) || "LIDER".equals(role)}) quando queria saber a
 * CAPACIDADE ("pode gerenciar inscrições?"). A mesma regra reimplementada em dezenas de
 * lugares tem dois custos: renomear um perfil vira caçada, e uma divergência entre duas
 * cópias é um furo de autorização SILENCIOSO — não quebra compilação nem teste.
 *
 * <p><b>Como usar:</b> chame o método com o nome da ação. Se a pergunta que você precisa
 * fazer não está aqui, adicione um método — não compare string no seu service.
 *
 * <p>Isto NÃO substitui o {@code SecurityConfig}: lá fica a trava por rota, aqui a regra
 * fina de dentro do serviço. As duas leem os mesmos perfis.
 */
public final class Permissoes {

    private Permissoes() {}

    private static final Set<Perfil> GESTORES = EnumSet.of(Perfil.ADMIN_IGREJA, Perfil.LIDER);
    private static final Set<Perfil> SO_ADMIN = EnumSet.of(Perfil.ADMIN_IGREJA);

    private static boolean tem(String nomeRole, Set<Perfil> permitidos) {
        Perfil role = Perfil.deNomeOuNull(nomeRole);
        return role != null && permitidos.contains(role);
    }

    /** Cancelar inscrição de outra pessoa, inscrever terceiros, remover convidado alheio. */
    public static boolean podeGerenciarInscricoes(String role) { return tem(role, GESTORES); }

    /** Lista completa de inscritos — inclui telefone de convidado e quem inscreveu quem. */
    public static boolean podeVerListaCompletaDeInscritos(String role) { return tem(role, GESTORES); }

    /** Endereço e observações de uma pessoa. */
    public static boolean podeVerDadosSensiveisDePessoa(String role) { return tem(role, SO_ADMIN); }

    /** Cadastrar, editar e arquivar pessoas. */
    public static boolean podeGerenciarPessoas(String role) { return tem(role, SO_ADMIN); }

    /** Criar, editar e arquivar eventos. */
    public static boolean podeGerenciarEventos(String role) { return tem(role, GESTORES); }

    /** Movimentações, categorias, relatórios e dashboard. */
    public static boolean podeVerFinanceiro(String role) { return tem(role, SO_ADMIN); }

    /**
     * Busca global: além de membros e eventos (visíveis a todos), inclui usuários e dados
     * financeiros (movimentações e categorias) nos resultados. Gate único porque a regra
     * cobre os dois — nomear só por um dos dois enganaria quem lê.
     */
    public static boolean podeVerUsuariosEFinanceiroNaBuscaGlobal(String role) { return tem(role, SO_ADMIN); }
}
