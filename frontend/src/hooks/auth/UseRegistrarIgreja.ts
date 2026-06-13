import { useAuthStore } from "@/store/authStore";
import { registrarIgrejaSchema1, type RegistrarIgrejaFormData1, registrarIgrejaSchema2, type RegistrarIgrejaFormData2 } from "@/lib/validators";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { authService } from "@/services/auth.service";
import axios from "axios";
import { useAppForm } from "../forms/useAppForm";
import type { ApiError } from "@/types/api.types";


export function useRegistrarIgreja () {
    const router = useRouter()
    const login = useAuthStore(state => state.login)
    const [erroGeral, setErroGeral] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(false)
    const [passo, setPasso] = useState<1 | 2>(1)
    const [dataPasso1, setDataPasso1] = useState<RegistrarIgrejaFormData1 | null>(null)

    const {
        register,
        handleSubmit,
        setValue,
        setError,
        formState: { errors },
        isFormIncomplete: passo1Incompleto,
    } = useAppForm<RegistrarIgrejaFormData1>({
        resolver: zodResolver(registrarIgrejaSchema1),
        defaultValues: {
            nomeIgreja: '',
            emailContato: '',
            telefoneContato: '',
        },
        requiredFields: ['nomeIgreja', 'emailContato', 'telefoneContato'],
    })

    const {
        register: register2,
        handleSubmit: handleSubmit2,
        watch: watch2,
        setError: setError2,
        formState: { errors: errors2 },
        isFormIncomplete: passo2Incompleto,
    } = useAppForm<RegistrarIgrejaFormData2>({
        resolver: zodResolver(registrarIgrejaSchema2),
        defaultValues: {
            nomeAdmin: '',
            emailAdmin: '',
            senhaAdmin: '',
            confirmarSenha: '',
            aceitouTermos: false,
        },
        requiredFields: ['nomeAdmin', 'emailAdmin', 'senhaAdmin', 'confirmarSenha'],
        requiredChecks: ['aceitouTermos'],
    })

    const irParaPasso2 = (data : RegistrarIgrejaFormData1) => {
        setDataPasso1(data)
        setPasso(2)
    }

    const voltarParaPasso1 = () => {
        setPasso(1)
    }

    const onSubmit = async (dataPasso2: RegistrarIgrejaFormData2) => {
        if(!dataPasso1) return
        
        setErroGeral(null)
        setIsLoading(true)

        try {
            const { confirmarSenha, aceitouTermos, ...dadosAdmin } = dataPasso2

            const dadosIgreja = {
                ...dataPasso1,
                telefoneContato: dataPasso1.telefoneContato.replace(/\D/g, ''),
                cnpj: dataPasso1.cnpj?.replace(/\D/g, '') || undefined,
            }
            const response = await authService.registrarIgreja({
                ...dadosIgreja,
                ...dadosAdmin,    
            })
            login({
                token : response.token,
                nome : response.nome,
                role : response.role,
                igrejaId : response.igrejaId,
            })
            router.push('/')

        } catch (error : unknown) {
            if (axios.isAxiosError<ApiError>(error)) {
                const data = error.response?.data
                const codigo = data?.error
                if (codigo === 'CNPJ_DUPLICADO') {
                    setError('cnpj', { type: 'server', message: data?.message })
                    setPasso(1)
                    return
                }
                if (codigo === 'EMAIL_DUPLICADO') {
                    setError2('emailAdmin', { type: 'server', message: data?.message })
                    return
                }

                setErroGeral(data?.message ?? 'Erro ao registrar igreja. Tente novamente.')
            } else {
                setErroGeral('Erro ao registrar igreja. Tente novamente.')
            }

        } finally {
            setIsLoading(false)
        }
    }
    
    return {
        passo,
        irParaPasso2,
        voltarParaPasso1,
        register,
        handleSubmit,
        setValue,
        errors,
        watch2,
        register2,
        handleSubmit2,
        errors2,
        erroGeral,
        isLoading,
        onSubmit,
        passo1Incompleto,
        passo2Incompleto
    }
}