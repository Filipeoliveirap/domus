import { loginSchema, type LoginFormData } from "@/lib/validators";
import { authService } from "@/services/auth.service";
import { useAuthStore } from "@/store/authStore";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from 'next/navigation'
import { useState } from "react";
import axios from 'axios'
import { useAppForm } from "../forms/useAppForm";
import type { ApiError } from "@/types/api.types";

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
                nome: response.nome,
                role: response.role,
                igrejaId: response.igrejaId,
                token: response.token,
            })
            router.push('/')
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const mensagem = error.response?.data?.message
                setErroGeral(mensagem ?? 'Erro ao fazer login. Tente novamente.')
                
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