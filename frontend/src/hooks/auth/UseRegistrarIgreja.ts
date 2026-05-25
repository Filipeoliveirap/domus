import { useAuthStore } from "@/store/authStore";
import { registrarIgrejaSchema1, type RegistrarIgrejaFormData1, registrarIgrejaSchema2, type RegistrarIgrejaFormData2 } from "@/lib/validators";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { authService } from "@/services/auth.service";
import axios from "axios";


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
        formState: { errors, isValid },
    } = useForm<RegistrarIgrejaFormData1>({
        resolver: zodResolver(registrarIgrejaSchema1),
        mode : 'onBlur',
    })

    const {
        register: register2,
        handleSubmit: handleSubmit2,
        formState: { errors: errors2, isValid: isValid2 },
    } = useForm<RegistrarIgrejaFormData2>({
        resolver: zodResolver(registrarIgrejaSchema2),
        mode : 'onBlur',
        
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
            const response = await authService.registrarIgreja({ ...dataPasso1, ...dataPasso2 })
            login({
                token : response.token,
                nome : response.nome,
                role : response.role,
                igrejaId : response.igrejaId,
            })
            router.push('/')

        } catch (error : unknown) {
            if (axios.isAxiosError(error)) {
                const mensagem = error.response?.data?.message
                setErroGeral(mensagem || 'Erro ao registrar igreja. Tente novamente.')
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
        errors,
        isValid,
        register2,
        handleSubmit2,
        errors2,
        isValid2,
        erroGeral,
        isLoading,
        onSubmit,
    }
}