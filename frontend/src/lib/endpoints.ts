
export const Endpoints = {
  inicio: {
    GET: '/inicio',
  },
  dashboard: {
    GET: '/dashboard',
  },
  auth: {
    LOGIN: '/auth/login',
    GOOGLE_LOGIN: '/auth/google/login',
    GOOGLE_REGISTRAR: '/auth/google/registrar',
    REFRESH: '/auth/refresh',
    LOGOUT: '/auth/logout',
    ME: '/auth/me',
    FORGOT_PASSWORD: '/auth/forgot-password',
    RESET_PASSWORD: '/auth/reset-password',
    REGISTER_IGREJA: '/igrejas/registrar',
  },
  usuarios: {
    LISTAR_USUARIOS: '/usuarios',
    BY_ID: (id: string) => `/usuarios/${id}`,
    STATUS: (id: string) => `/usuarios/${id}/status`,
    ROLE: (id: string) => `/usuarios/${id}/role`,
    CONCEDER_ACESSO: '/usuarios/conceder-acesso',
    REATIVAR_ACESSO: '/usuarios/reativar-acesso',
    REENVIAR_CONVITE: (id: string) => `/usuarios/${id}/reenviar-convite`,
  },
  pessoas: {
    LISTAR: '/pessoas',
    CRIAR: '/pessoas',
    BY_ID: (id: string) => `/pessoas/${id}`,
    ARQUIVAR: (id: string) => `/pessoas/${id}`,
    BAIRROS: '/pessoas/bairros',
  },

  eventos: {
    LISTAR: '/eventos',
    CRIAR: '/eventos',
    BY_ID: (id: string) => `/eventos/${id}`,
  },

  inscricoes: {
    INSCREVER: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    MINHA: (eventoId: string) => `/eventos/${eventoId}/inscricoes/minha`,
    INSCREVER_MEMBROS: (eventoId: string) => `/eventos/${eventoId}/inscricoes/pessoas`,
    ACOMPANHANTES: (eventoId: string, inscricaoId: string) =>
      `/eventos/${eventoId}/inscricoes/${inscricaoId}/acompanhantes`,
    PARTICIPANTES: (eventoId: string) => `/eventos/${eventoId}/inscricoes/participantes`,
    LISTAR: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    CANCELAR: (inscricaoId: string) => `/inscricoes/${inscricaoId}`,
    REMOVER_ACOMPANHANTE: (acompanhanteId: string) => `/acompanhantes/${acompanhanteId}`,
  },

  categorias: {
    base: '/categorias',
    todas: '/categorias/todas',
    porId: (id: string) => `/categorias/${id}`,
    contagemMovimentacoes: (id: string) => `/categorias/${id}/contagem-movimentacoes`,
  },

  movimentacoes: {
    base: '/movimentacoes',
    porId: (id: string) => `/movimentacoes/${id}`,
  },

  relatorios: {
    resumo: '/relatorios/resumo',
    porCategoria: '/relatorios/por-categoria',
    evolucaoMensal: '/relatorios/evolucao-mensal',
    maiorLancamento: '/relatorios/maior-lancamento',
    congregacoes: '/relatorios/congregacoes',
  },

  igreja: {
    MINHA: '/igrejas/minha',
  },

  igrejasVinculadas: {
    STATUS: '/igrejas-vinculadas',
    GERAR_CODIGO: '/igrejas-vinculadas/codigo',
    ENTRAR: '/igrejas-vinculadas/entrar',
    DESVINCULAR: (congregacaoId: string) => `/igrejas-vinculadas/congregacoes/${congregacaoId}`,
    SAIR: '/igrejas-vinculadas/sair',
  },
}