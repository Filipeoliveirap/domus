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

// Sem token de propósito: viajam em cookie httpOnly, JavaScript nunca os vê.
export interface Sessao {
    id: string;
    nome: string;
    role: Role;
    igrejaId: string;
    igrejaNome: string;
    /** Foto da PESSOA vinculada (Usuario não tem foto própria). null = sem foto. */
    fotoId: string | null;
    /** Cargo da pessoa (Pastor, Missionário…) — exibido na sidebar no lugar da role. */
    cargo: string | null;
    /** Sigla da igreja (IBC, SIBAPI…) — exibida no header. */
    igrejaSigla: string | null;
    /** Logo da igreja — exibida no ícone do TopBar no lugar do Church quando existe. */
    igrejaLogoId: string | null;
    /** Capacidades extras acumuladas (SECRETARIO, TESOUREIRO). */
    capacidadesExtras?: string[];
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

export interface AlterarSenhaRequest {
    senhaAtual: string;
    novaSenha: string;
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
