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