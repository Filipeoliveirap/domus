import { Role } from "./usuario.types";
export interface LoginRequest {
    email: string;
    senha: string;
}

export interface GoogleRegistrarRequest {
    idToken: string;
    nomeIgreja: string;
    cnpj?: string;
    telefoneContato: string;
}

/**
 * O que o backend devolve sobre a sessão — em /auth/login, /auth/google/*,
 * /igrejas/registrar e /auth/me.
 *
 * Sem token de propósito: eles viajam em cookie httpOnly e o JavaScript nunca os vê.
 */
export interface Sessao {
    id: string;
    nome: string;
    role: Role;
    igrejaId: string;
    igrejaNome: string;
}

export interface ForgotPasswordRequest {
    email: string;
}

export interface ResetPasswordRequest {
    token: string;
    novaSenha: string;
}

export interface MensagemResponse {
    message: string;
}

export interface RegistrarIgrejaRequest {
    nomeIgreja : string;
    emailContato : string;
    cnpj? : string;
    telefoneContato? : string;
    nomeAdmin : string;
    emailAdmin : string;
    senhaAdmin : string;
}
