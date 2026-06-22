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
    token: string;
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
    nome : string;
    role : Role;
    igrejaId : string;
}