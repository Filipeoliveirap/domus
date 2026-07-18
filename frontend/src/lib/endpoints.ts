
export const Endpoints = {
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
  },
  membros: {
    LISTAR: '/membros',
    CRIAR: '/membros',
    BY_ID: (id: string) => `/membros/${id}`,
    ARQUIVAR: (id: string) => `/membros/${id}`,
    BAIRROS: '/membros/bairros',
  },

  eventos: {
    LISTAR: '/eventos',
    CRIAR: '/eventos',
    BY_ID: (id: string) => `/eventos/${id}`,
  },

  categorias: {
    base: '/categorias',
    todas: '/categorias/todas',
    porId: (id: string) => `/categorias/${id}`,
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
  },
}