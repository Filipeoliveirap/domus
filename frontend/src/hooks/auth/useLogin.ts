import { loginSchema, type LoginFormData } from "@/lib/validators";
import { authService } from "@/services/auth.service";
import { useAuthStore } from "@/store/authStore";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from 'next/navigation'
import { useState } from "react";
import axios from 'axios'
import { useAppForm } from "../forms/useAppForm";
import type { ApiError } from "@/types/api.types";
import type { LoginResponse } from "@/types/auth.types";

function destinoSeguro(next: string | null) {
    if (!next || !next.startsWith('/') || next.startsWith('//')) return '/inicio'
    return next
}

// Mensagem de rate limit (429), enriquecida com o tempo de espera do header Retry-After.
function mensagemRateLimit(error: import('axios').AxiosError<ApiError>) {
    const retryAfter = Number(error.response?.headers?.['retry-after'])
    if (Number.isFinite(retryAfter) && retryAfter > 0) {
        return `Muitas tentativas em pouco tempo. Aguarde ${retryAfter} segundos e tente novamente.`
    }
    return error.response?.data?.message ?? 'Muitas tentativas em pouco tempo. Aguarde um instante e tente novamente.'
}

export function useLogin() {
    const router = useRouter()
    const login = useAuthStore(state => state.login)
    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)
    // Login nativo numa conta só-Google: guardamos o e-mail digitado para oferecer
    // "Definir senha" (fluxo de reset) já pré-preenchido.
    const [contaSemSenha, setContaSemSenha] = useState(false)
    const [emailDigitado, setEmailDigitado] = useState('')

    const {
        register,
        handleSubmit,
        isFormIncomplete,
        formState: { errors },
    } = useAppForm<LoginFormData>({
        resolver: zodResolver(loginSchema),
        defaultValues: {
            email: '',
            senha: '',
        },
        requiredFields: ['email', 'senha'],
    })

    const isButtonDisabled = isFormIncomplete || isLoading

    // Grava a sessão no store e redireciona. Compartilhado entre login nativo e Google.
    function aplicarSessao(response: LoginResponse) {
        login({
            id: response.id,
            nome: response.nome,
            role: response.role,
            igrejaId: response.igrejaId,
            igrejaNome: response.igrejaNome,
            token: response.token,
            refreshToken: response.refreshToken,
        })
        const next = new URLSearchParams(window.location.search).get('next')
        router.push(destinoSeguro(next))
    }

    const onSubmit = async (data: LoginFormData) => {
        setErroGeral(null)
        setContaSemSenha(false)
        setIsLoading(true)
        try {
            const response = await authService.login(data)
            aplicarSessao(response)
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const e = error.response?.data
                if (e?.error === 'CONTA_SEM_SENHA') {
                    setEmailDigitado(data.email)
                    setContaSemSenha(true)
                    return
                }
                if (e?.error === 'CONTA_ARQUIVADA') {
                    setErroGeral(e.message)
                    return
                }
                if (e?.error === 'RATE_LIMIT_EXCEDIDO') {
                    setErroGeral(mensagemRateLimit(error))
                    return
                }
                setErroGeral(e?.message ?? 'Erro ao fazer login. Tente novamente.')
            } else {
                setErroGeral('Erro ao fazer login. Tente novamente.')
            }
        } finally {
            setIsLoading(false)
        }
    }

    const onGoogleLogin = async (idToken: string) => {
        setErroGeral(null)
        setContaSemSenha(false)
        setIsLoading(true)
        try {
            const response = await authService.googleLogin(idToken)
            aplicarSessao(response)
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const e = error.response?.data
                if (e?.error === 'CONTA_NAO_ENCONTRADA') {
                    setErroGeral(e.message)
                    return
                }
                if (e?.error === 'RATE_LIMIT_EXCEDIDO') {
                    setErroGeral(mensagemRateLimit(error))
                    return
                }
                setErroGeral(e?.message ?? 'Não foi possível entrar com o Google. Tente novamente.')
            } else {
                setErroGeral('Não foi possível entrar com o Google. Tente novamente.')
            }
        } finally {
            setIsLoading(false)
        }
    }

    return {
        register,
        handleSubmit,
        errors,
        erroGeral,
        isLoading,
        onSubmit,
        onGoogleLogin,
        onGoogleError: () => setErroGeral('Não foi possível iniciar o login com Google.'),
        isButtonDisabled,
        contaSemSenha,
        emailDigitado,
    }
}
