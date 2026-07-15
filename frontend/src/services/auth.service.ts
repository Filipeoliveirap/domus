import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { ForgotPasswordRequest, GoogleRegistrarRequest, LoginRequest, LoginResponse, MensagemResponse, RegistrarIgrejaRequest, RegistrarIgrejaResponse, ResetPasswordRequest, TokenPair} from "@/types/auth.types";

export const authService = {
    login: (data: LoginRequest) : Promise<LoginResponse> =>
        api.post<LoginResponse>(Endpoints.auth.LOGIN, data).then(res => res.data),
    googleLogin: (idToken: string) : Promise<LoginResponse> =>
        api.post<LoginResponse>(Endpoints.auth.GOOGLE_LOGIN, { idToken }).then(res => res.data),
    googleRegistrar: (data: GoogleRegistrarRequest) : Promise<RegistrarIgrejaResponse> =>
        api.post<RegistrarIgrejaResponse>(Endpoints.auth.GOOGLE_REGISTRAR, data).then(res => res.data),
    refresh: (refreshToken: string) : Promise<TokenPair> =>
        api.post<TokenPair>(Endpoints.auth.REFRESH, { refreshToken }).then(res => res.data),
    logout: (refreshToken: string) : Promise<void> =>
        api.post(Endpoints.auth.LOGOUT, { refreshToken }).then(() => undefined),
    forgotPassword: (data: ForgotPasswordRequest) : Promise<MensagemResponse> =>
        api.post<MensagemResponse>(Endpoints.auth.FORGOT_PASSWORD, data).then(res => res.data),
    resetPassword: (data: ResetPasswordRequest) : Promise<MensagemResponse> =>
        api.post<MensagemResponse>(Endpoints.auth.RESET_PASSWORD, data).then(res => res.data),
    registrarIgreja: (data : RegistrarIgrejaRequest) : Promise<RegistrarIgrejaResponse> =>
        api.post<RegistrarIgrejaResponse>(Endpoints.auth.REGISTER_IGREJA, data).then(res => res.data),
}

