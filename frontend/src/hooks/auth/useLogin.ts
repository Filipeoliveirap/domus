import { loginSchema, type LoginFormData } from "@/lib/validators";
import { authService } from "@/services/auth.service";
import { useAuthStore } from "@/store/authStore";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from 'next/navigation'
import { useState } from "react";
import { useForm } from "react-hook-form";
import axios from 'axios'

export function useLogin() {
    const router = useRouter()
    const login = useAuthStore(state => state.login)
    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)

    const {
        register,
        handleSubmit,
        watch,
        formState: { errors },
    } = useForm<LoginFormData>({
        resolver: zodResolver(loginSchema),
        mode: 'onTouched',
        reValidateMode: 'onChange',
    })

    const emailValue = watch('email')
    const senhaValue = watch('senha')

    const isButtonDisabled =
        !emailValue?.trim() ||
        !senhaValue?.trim() ||
        isLoading

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
            if (axios.isAxiosError(error)) {
                const mensagem = error.response?.data?.message
                setErroGeral(mensagem || 'Erro ao fazer login. Tente novamente.')
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