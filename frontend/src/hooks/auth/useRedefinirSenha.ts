import { redefinirSenhaSchema, type RedefinirSenhaFormData } from "@/lib/validators";
import { authService } from "@/services/auth.service";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from 'next/navigation'
import { useState } from "react";
import axios from 'axios'
import { useAppForm } from "../forms/useAppForm";
import type { ApiError } from "@/types/api.types";

export function useRedefinirSenha() {
    const router = useRouter()
    const searchParams = useSearchParams()
    const token = searchParams.get('token') ?? ''

    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)
    const [sucesso, setSucesso] = useState(false)
    const [tokenInvalido, setTokenInvalido] = useState(false)

    const {
        register,
        handleSubmit,
        watch,
        isFormIncomplete,
        formState: { errors },
    } = useAppForm<RedefinirSenhaFormData>({
        resolver: zodResolver(redefinirSenhaSchema),
        defaultValues: { novaSenha: '', confirmarSenha: '' },
        requiredFields: ['novaSenha', 'confirmarSenha'],
    })

    // Link inválido: sem token na URL, ou o backend rejeitou (expirado / já usado).
    const linkInvalido = token.trim().length === 0 || tokenInvalido
    const isButtonDisabled = isFormIncomplete || isLoading

    const onSubmit = async (data: RedefinirSenhaFormData) => {
        setErroGeral(null)
        setIsLoading(true)
        try {
            await authService.resetPassword({ token, novaSenha: data.novaSenha })
            setSucesso(true)
            // Dá tempo de ler a confirmação antes de mandar para o login.
            setTimeout(() => router.push('/login'), 2500)
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const e = error.response?.data
                if (e?.error === 'TOKEN_INVALIDO') {
                    setTokenInvalido(true)
                    return
                }
                setErroGeral(e?.message ?? 'Não foi possível redefinir a senha. Tente novamente.')
            } else {
                setErroGeral('Não foi possível redefinir a senha. Tente novamente.')
            }
        } finally {
            setIsLoading(false)
        }
    }

    return {
        register,
        handleSubmit,
        watch,
        errors,
        erroGeral,
        isLoading,
        isButtonDisabled,
        onSubmit,
        sucesso,
        linkInvalido,
    }
}
