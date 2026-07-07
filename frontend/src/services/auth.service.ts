import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { LoginRequest, LoginResponse, RegistrarIgrejaRequest, RegistrarIgrejaResponse} from "@/types/auth.types";

export const authService = {
    login: (data: LoginRequest) : Promise<LoginResponse> => 
        api.post<LoginResponse>(Endpoints.auth.LOGIN, data).then(res => res.data),
    registrarIgreja: (data : RegistrarIgrejaRequest) : Promise<RegistrarIgrejaResponse> =>
        api.post<RegistrarIgrejaResponse>(Endpoints.auth.REGISTER_IGREJA, data).then(res => res.data),
}

