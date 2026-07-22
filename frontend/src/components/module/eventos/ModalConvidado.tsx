'use client'

import { useEffect } from 'react'
import { X, Info } from 'lucide-react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { useAdicionarConvidado } from '@/hooks/inscricao/useAdicionarConvidado'
import { formatarTelefone } from '@/lib/masks'
import { convidadoSchema, type ConvidadoFormData, type ConvidadoFormInput } from '@/lib/validators'
import styles from './ModalConvidado.module.css'

interface Props {
  eventoId: string
  inscricaoId: string
  onClose: () => void
}

/** Adiciona um convidado ("acompanhante" no backend) à própria inscrição do usuário. */
export function ModalConvidado({ eventoId, inscricaoId, onClose }: Props) {
  const adicionarConvidado = useAdicionarConvidado(eventoId, inscricaoId)

  const {
    register,
    handleSubmit,
    setFocus,
    setValue,
    formState: { errors },
  } = useForm<ConvidadoFormInput, unknown, ConvidadoFormData>({
    resolver: zodResolver(convidadoSchema),
    defaultValues: { nome: '', telefone: '' },
  })

  useEffect(() => {
    setFocus('nome')
  }, [setFocus])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !adicionarConvidado.isPending) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, adicionarConvidado.isPending])

  function onSubmit(data: ConvidadoFormData) {
    adicionarConvidado.mutate(data, { onSuccess: () => onClose() })
  }

  return (
    <div className={styles.overlay} onMouseDown={() => !adicionarConvidado.isPending && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-convidado"
      >
        <div className={styles.header}>
          <div>
            <h2 className={styles.titulo} id="titulo-convidado">Vou levar alguém de fora</h2>
            <p className={styles.subtitulo}>
              Essa pessoa entra na lista como seu convidado e ocupa uma vaga.
            </p>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={adicionarConvidado.isPending}
          >
            <X size={20} />
          </button>
        </div>

        <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
          <Input
            id="nome"
            label="NOME DO CONVIDADO*"
            placeholder="Ex: Maria Souza"
            error={errors.nome?.message}
            {...register('nome')}
          />

          <Input
            id="telefone"
            label="TELEFONE (OPCIONAL)"
            placeholder="(00) 00000-0000"
            inputMode="numeric"
            error={errors.telefone?.message}
            {...register('telefone')}
            onChange={(e) => setValue('telefone', formatarTelefone(e.target.value), { shouldValidate: true })}
          />

          <div className={styles.infoBox}>
            <Info size={18} className={styles.infoIcon} aria-hidden="true" />
            <p className={styles.infoText}>
              Convidados aparecem na lista de inscritos vinculados ao seu nome.
            </p>
          </div>

          <div className={styles.footer}>
            <button
              type="button"
              className={styles.btnCancelar}
              onClick={onClose}
              disabled={adicionarConvidado.isPending}
            >
              Cancelar
            </button>
            <Button type="submit" variant="primary" size="md" isLoading={adicionarConvidado.isPending}>
              Adicionar convidado
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
