export type Role = 'ADMIN_IGREJA' | 'LIDER' | 'MEMBRO';

export interface UsuarioRequest {
    nomeUsuario: string;
    emailUsuario: string;
    senhaUsuario: string;
    role: Role;
}

export interface UsuarioResponse {
  id: string;
  nome: string;
  email: string;
  role: string;
  ativo: boolean;
  ultimoLoginEm: string | null;
  criadoEm: string;
}

export interface UsuarioUpdateRequest {
  nome: string;
  email: string;
  role: Role;
}

export interface PagedResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}