import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { LoginRequest, LoginResponse, RegistrarIgrejaRequest, RegistrarIgrejaResponse, TokenPair} from "@/types/auth.types";

export const authService = {
    login: (data: LoginRequest) : Promise<LoginResponse> =>
        api.post<LoginResponse>(Endpoints.auth.LOGIN, data).then(res => res.data),
    refresh: (refreshToken: string) : Promise<TokenPair> =>
        api.post<TokenPair>(Endpoints.auth.REFRESH, { refreshToken }).then(res => res.data),
    logout: (refreshToken: string) : Promise<void> =>
        api.post(Endpoints.auth.LOGOUT, { refreshToken }).then(() => undefined),
    registrarIgreja: (data : RegistrarIgrejaRequest) : Promise<RegistrarIgrejaResponse> =>
        api.post<RegistrarIgrejaResponse>(Endpoints.auth.REGISTER_IGREJA, data).then(res => res.data),
}

