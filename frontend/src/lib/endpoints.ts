
export const Endpoints = {
  auth: {
    LOGIN: '/auth/login',
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
  },

  eventos: {
    LISTAR: '/eventos',
    CRIAR: '/eventos',
    BY_ID: (id: string) => `/eventos/${id}`,
  },
}