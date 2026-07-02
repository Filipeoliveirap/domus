export type Role = 'ADMIN_IGREJA' | 'LIDER' | 'MEMBRO';

export interface UsuarioResponse {
  id: string;
  nome: string;
  email: string;
  role: string;
  ativo: boolean;
  ultimoLoginEm: string | null;
  criadoEm: string;
}

export interface PagedResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}