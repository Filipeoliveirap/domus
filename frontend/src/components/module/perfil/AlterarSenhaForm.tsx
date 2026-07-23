'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import axios from 'axios'
import { Lock } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAlterarSenha } from '@/hooks/auth/useAlterarSenha'
import { alterarSenhaSchema, type AlterarSenhaFormData } from '@/lib/validators'
import type { ApiError } from '@/types/api.types'
import styles from './AlterarSenhaForm.module.css'

export function AlterarSenhaForm() {
  const { register, handleSubmit, reset, setError, formState: { errors } } =
    useForm<AlterarSenhaFormData>({ resolver: zodResolver(alterarSenhaSchema) })
  const { mutate, isPending } = useAlterarSenha()

  const onSubmit = (data: AlterarSenhaFormData) => {
    mutate(
      { senhaAtual: data.senhaAtual, novaSenha: data.novaSenha },
      {
        onSuccess: () => {
          notificar.sucesso('Senha alterada com sucesso.')
          reset()
        },
        onError: (error) => {
          if (axios.isAxiosError<ApiError>(error)) {
            const e = error.response?.data
            if (e?.error === 'SENHA_ATUAL_INCORRETA') {
              setError('senhaAtual', { type: 'server', message: e.message })
              return
            }
            if (e?.error === 'CONTA_SEM_SENHA') {
              notificar.erro(e.message)
              return
            }
            notificar.erro(e?.message ?? 'Erro ao alterar senha. Tente novamente.')
          } else {
            notificar.erro('Erro ao alterar senha. Tente novamente.')
          }
        },
      },
    )
  }

  return (
    <section className={styles.secao}>
      <div className={styles.header}>
        <Lock size={18} />
        <div>
          <h2 className={styles.titulo}>Alterar senha</h2>
          <p className={styles.subtitulo}>Proteja sua conta com uma senha forte.</p>
        </div>
      </div>
      <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
        <Input id="senhaAtual" type="password" label="SENHA ATUAL*"
          error={errors.senhaAtual?.message} {...register('senhaAtual')} />
        <div className={styles.grid2}>
          <Input id="novaSenha" type="password" label="NOVA SENHA*" placeholder="Mínimo 8 caracteres"
            error={errors.novaSenha?.message} {...register('novaSenha')} />
          <Input id="confirmarNovaSenha" type="password" label="CONFIRMAR NOVA SENHA*"
            error={errors.confirmarNovaSenha?.message} {...register('confirmarNovaSenha')} />
        </div>
        <Button type="submit" variant="primary" isLoading={isPending} disabled={isPending}>
          Alterar senha
        </Button>
      </form>
    </section>
  )
}
