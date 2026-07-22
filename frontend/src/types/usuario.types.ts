export type Role = 'ADMIN_IGREJA' | 'LIDER' | 'ACESSO_COMUM';

export interface UsuarioResponse {
  id: string;
  nome: string;
  email: string;
  role: string;
  ativo: boolean;
  ultimoLoginEm: string | null;
  convitePendente: boolean;
  criadoEm: string;
}

