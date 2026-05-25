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

export interface ApiError {
    status: number;
    erro: string;
    mensagem: string;
    timestamp: string;
    campos?: Record<string, string>
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