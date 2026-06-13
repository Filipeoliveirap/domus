export interface LoginRequest {
    email: string;
    senha: string;
}

export interface LoginResponse {
    nome: string;
    role: string;
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
    token : string;
    nome : string;
    role : string;
    igrejaId : string;
}