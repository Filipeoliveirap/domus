import { loginSchema, type LoginFormData } from "@/lib/validators";
import { authService } from "@/services/auth.service";
import { useAuthStore } from "@/store/authStore";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from 'next/navigation'
import { useState } from "react";
import axios from 'axios'
import { useAppForm } from "../forms/useAppForm";
import type { ApiError } from "@/types/api.types";

function destinoSeguro(next: string | null) {
    if (!next || !next.startsWith('/') || next.startsWith('//')) return '/inicio'
    return next
}

export function useLogin() {
    const router = useRouter()
    const login = useAuthStore(state => state.login)
    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)

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

    const onSubmit = async (data: LoginFormData) => {
        setErroGeral(null)
        setIsLoading(true)
        try {
            const response = await authService.login(data)
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
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const e = error.response?.data
                if (e?.error === 'CONTA_ARQUIVADA') {
                    setErroGeral(e.message)
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

    return {
        register,
        handleSubmit,
        errors,
        erroGeral,
        isLoading,
        onSubmit,
        isButtonDisabled,
    }
}