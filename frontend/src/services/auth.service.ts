import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { LoginRequest, LoginResponse } from "@/types/auth.types";

export const authService = {
    login: (data: LoginRequest) : Promise<LoginResponse> => 
        api.post<LoginResponse>(Endpoints.auth.LOGIN, data).then(res => res.data),
}