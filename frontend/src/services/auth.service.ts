import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { AlterarSenhaRequest, ForgotPasswordRequest, GoogleRegistrarRequest, LoginRequest, MensagemResponse, RegistrarIgrejaRequest, ResetPasswordRequest, Sessao} from "@/types/auth.types";

// Não há `refresh` aqui de propósito: o refresh é disparado só de dentro do interceptor do
// api.ts, e importar este service lá criaria um ciclo (authService -> api -> authService).

// As rotas públicas de auth (sem sessão prévia) agora também exigem o token CSRF de
// double-submit — antes eram isentas (ver BACKLOG, "Login CSRF"). Sem cookie XSRF-TOKEN
// ainda, um GET /auth/me (mesmo respondendo 401) já basta pra gravá-lo: a resolução do
// token no backend é eager (`csrfTokenRequestHandler`), roda antes da checagem de sessão.
let primingCsrf: Promise<void> | null = null;

function garantirCsrfCookie(): Promise<void> {
    if (typeof document !== "undefined" && document.cookie.includes("XSRF-TOKEN=")) {
        return Promise.resolve();
    }
    if (!primingCsrf) {
        primingCsrf = api.get(Endpoints.auth.ME).catch(() => undefined).then(() => undefined);
    }
    return primingCsrf;
}

export const authService = {
    login: async (data: LoginRequest) : Promise<Sessao> => {
        await garantirCsrfCookie();
        return api.post<Sessao>(Endpoints.auth.LOGIN, data).then(res => res.data);
    },
    googleLogin: async (idToken: string) : Promise<Sessao> => {
        await garantirCsrfCookie();
        return api.post<Sessao>(Endpoints.auth.GOOGLE_LOGIN, { idToken }).then(res => res.data);
    },
    googleRegistrar: async (data: GoogleRegistrarRequest) : Promise<Sessao> => {
        await garantirCsrfCookie();
        return api.post<Sessao>(Endpoints.auth.GOOGLE_REGISTRAR, data).then(res => res.data);
    },
    /** Quem sou eu? O servidor é o dono da verdade — o JS não lê o cookie httpOnly. */
    me: () : Promise<Sessao> =>
        api.get<Sessao>(Endpoints.auth.ME).then(res => res.data),
    /** Sem argumento: o refresh vai no cookie. O servidor é quem expira os dois cookies. */
    logout: () : Promise<void> =>
        api.post(Endpoints.auth.LOGOUT).then(() => undefined),
    forgotPassword: async (data: ForgotPasswordRequest) : Promise<MensagemResponse> => {
        await garantirCsrfCookie();
        return api.post<MensagemResponse>(Endpoints.auth.FORGOT_PASSWORD, data).then(res => res.data);
    },
    resetPassword: async (data: ResetPasswordRequest) : Promise<MensagemResponse> => {
        await garantirCsrfCookie();
        return api.post<MensagemResponse>(Endpoints.auth.RESET_PASSWORD, data).then(res => res.data);
    },
    registrarIgreja: async (data : RegistrarIgrejaRequest) : Promise<Sessao> => {
        await garantirCsrfCookie();
        return api.post<Sessao>(Endpoints.auth.REGISTER_IGREJA, data).then(res => res.data);
    },
    alterarSenha: (data: AlterarSenhaRequest): Promise<{ message: string }> =>
        api.put<{ message: string }>(Endpoints.auth.CHANGE_PASSWORD, data).then(res => res.data),
}
