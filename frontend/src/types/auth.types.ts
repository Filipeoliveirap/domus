import { Role } from "./usuario.types";
export interface LoginRequest {
    email: string;
    senha: string;
}

export interface LoginResponse {
    id: string;
    nome: string;
    role: Role;
    igrejaId: string;
    igrejaNome: string;
    token: string;
    refreshToken: string;
}

export interface TokenPair {
    token: string;
    refreshToken: string;
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

export interface RegistrarIgrejaResponse {
    id: string
    token : string;
    refreshToken : string;
    nome : string;
    role : Role;
    igrejaId : string;
    igrejaNome : string;
}