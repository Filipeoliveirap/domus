import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { ForgotPasswordRequest, GoogleRegistrarRequest, LoginRequest, MensagemResponse, RegistrarIgrejaRequest, ResetPasswordRequest, Sessao} from "@/types/auth.types";

// Não há `refresh` aqui de propósito: o refresh é disparado só de dentro do interceptor do
// api.ts, e importar este service lá criaria um ciclo (authService -> api -> authService).

export const authService = {
    login: (data: LoginRequest) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.LOGIN, data).then(res => res.data),
    googleLogin: (idToken: string) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.GOOGLE_LOGIN, { idToken }).then(res => res.data),
    googleRegistrar: (data: GoogleRegistrarRequest) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.GOOGLE_REGISTRAR, data).then(res => res.data),
    /** Quem sou eu? O servidor é o dono da verdade — o JS não lê o cookie httpOnly. */
    me: () : Promise<Sessao> =>
        api.get<Sessao>(Endpoints.auth.ME).then(res => res.data),
    /** Sem argumento: o refresh vai no cookie. O servidor é quem expira os dois cookies. */
    logout: () : Promise<void> =>
        api.post(Endpoints.auth.LOGOUT).then(() => undefined),
    forgotPassword: (data: ForgotPasswordRequest) : Promise<MensagemResponse> =>
        api.post<MensagemResponse>(Endpoints.auth.FORGOT_PASSWORD, data).then(res => res.data),
    resetPassword: (data: ResetPasswordRequest) : Promise<MensagemResponse> =>
        api.post<MensagemResponse>(Endpoints.auth.RESET_PASSWORD, data).then(res => res.data),
    registrarIgreja: (data : RegistrarIgrejaRequest) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.REGISTER_IGREJA, data).then(res => res.data),
}
