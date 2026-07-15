import { esqueciSenhaSchema, type EsqueciSenhaFormData } from "@/lib/validators";
import { authService } from "@/services/auth.service";
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import axios from 'axios'
import { useAppForm } from "../forms/useAppForm";
import type { ApiError } from "@/types/api.types";

export function useEsqueciSenha() {
    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)
    const [enviado, setEnviado] = useState(false)
    const [emailEnviado, setEmailEnviado] = useState('')

    const {
        register,
        handleSubmit,
        isFormIncomplete,
        formState: { errors },
    } = useAppForm<EsqueciSenhaFormData>({
        resolver: zodResolver(esqueciSenhaSchema),
        defaultValues: { email: '' },
        requiredFields: ['email'],
    })

    const isButtonDisabled = isFormIncomplete || isLoading

    const onSubmit = async (data: EsqueciSenhaFormData) => {
        setErroGeral(null)
        setIsLoading(true)
        try {
            await authService.forgotPassword({ email: data.email })
            // Sucesso sempre genérico: mostramos "enviado" sem revelar se o e-mail existe.
            setEmailEnviado(data.email)
            setEnviado(true)
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                setErroGeral(error.response?.data?.message ?? 'Erro ao enviar. Tente novamente.')
            } else {
                setErroGeral('Erro ao enviar. Tente novamente.')
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
        isButtonDisabled,
        onSubmit,
        enviado,
        emailEnviado,
    }
}
