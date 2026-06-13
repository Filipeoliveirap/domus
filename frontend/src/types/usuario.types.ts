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
    role: Role;
    criadoEm: string;
}

