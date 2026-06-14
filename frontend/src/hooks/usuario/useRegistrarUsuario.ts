import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAppForm } from "../forms/useAppForm";
import { RegistrarUsuarioFormData, registrarUsuarioSchema } from "@/lib/validators";
import { zodResolver } from "@hookform/resolvers/zod";
import { usuarioService } from "@/services/usuarios.service";
import axios from "axios";
import type { ApiError } from "@/types/api.types";
import { toast } from "sonner";

export function useRegistrarUsuario() {
    const router = useRouter()
    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)

    const {
        register,
        handleSubmit,
        control,
        watch,
        setError,
        formState: { errors },
        isFormIncomplete,
    } = useAppForm<RegistrarUsuarioFormData>({
        resolver: zodResolver(registrarUsuarioSchema),
        defaultValues: {
            nomeUsuario: '',
            emailUsuario: '',
            senhaUsuario: '',
            confirmarSenha: '',
            role: 'MEMBRO',
        },
        requiredFields: ['nomeUsuario', 'emailUsuario', 'senhaUsuario', 'confirmarSenha', 'role'],
    })

    const onSubmit = async (data: RegistrarUsuarioFormData) => {
        setErroGeral(null)
        setIsLoading(true)
        
        try {
            const { confirmarSenha, ...dadosRequest } = data
            await usuarioService.registrarUsuario(dadosRequest)
            toast.success('Usuário cadastrado com sucesso!')
            router.push('/usuarios')
        } catch (error: unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const errorData = error.response?.data

                if (errorData?.error === 'EMAIL_DUPLICADO') {
                    setError('emailUsuario', { type: 'server', message: errorData.message })
                    return
                }

                setErroGeral(errorData?.message ?? 'Erro ao registrar usuário. Tente novamente.')

            } else {
                setErroGeral('Erro ao registrar usuário. Tente novamente.')
            }
        } finally {
            setIsLoading(false)
        }

    }

    return {
        register,
        handleSubmit,
        control,
        watch,
        setError,
        errors,
        isFormIncomplete,
        erroGeral,
        isLoading,
        onSubmit,
    }

}