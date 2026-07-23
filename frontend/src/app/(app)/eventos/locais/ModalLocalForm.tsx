'use client'

import { MapPin, X } from 'lucide-react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useLocalEventoForm } from '@/hooks/evento/useLocalEventoForm'
import { localEventoSchema, type LocalEventoFormData, type LocalEventoFormInput } from '@/lib/validators'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import type { LocalEventoRequest, LocalEventoResponse } from '@/types/evento.type'
import styles from './ModalLocalForm.module.css'

interface Props {
  /** Presente = edição; ausente = criação. */
  local: LocalEventoResponse | null
  onClose: () => void
}

export function ModalLocalForm({ local, onClose }: Props) {
  const { salvar, isLoading, erroGeral } = useLocalEventoForm(local, onClose)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LocalEventoFormInput, unknown, LocalEventoFormData>({
    resolver: zodResolver(localEventoSchema),
    defaultValues: {
      nome: local?.nome ?? '',
      capacidade: local?.capacidade ?? undefined,
      // Reidrata dos campos CRUS (não do `endereco` formatado, que colapsa tudo num texto).
      // Vêm null quando o local herda o endereço da igreja — nesse caso o form abre vazio, e
      // deixá-lo vazio no submit mantém a herança (não vira endereço próprio igual ao da igreja).
      cepLogradouroNumero: local?.cepLogradouroNumero ?? '',
      complementoBairroCidadeUf: local?.complementoBairroCidadeUf ?? '',
    },
  })

  const onSubmit = (data: LocalEventoFormData) => {
    const payload: LocalEventoRequest = {
      nome: data.nome,
      capacidade: data.capacidade ?? null,
      cepLogradouroNumero: data.cepLogradouroNumero || null,
      complementoBairroCidadeUf: data.complementoBairroCidadeUf || null,
    }
    salvar(payload)
  }

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className={styles.header}>
          <div className={styles.iconBox}>
            <MapPin size={24} />
          </div>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.intro}>
          <h2 className={styles.title}>{local ? 'Editar local' : 'Novo local'}</h2>
          <p className={styles.subtitle}>
            Deixe o endereço em branco para o local herdar o endereço da igreja.
          </p>
        </div>

        <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
          <Input
            id="local-nome"
            label="NOME"
            placeholder="Ex: Santuário Principal"
            error={errors.nome?.message}
            {...register('nome')}
          />

          <Input
            id="local-capacidade"
            type="number"
            min={1}
            label="CAPACIDADE (OPCIONAL)"
            placeholder="Ex: 200"
            error={errors.capacidade?.message}
            {...register('capacidade')}
          />

          <Input
            id="local-endereco"
            label="ENDEREÇO (OPCIONAL)"
            placeholder="CEP, logradouro, número"
            error={errors.cepLogradouroNumero?.message}
            {...register('cepLogradouroNumero')}
          />

          <Input
            id="local-complemento"
            label="COMPLEMENTO / BAIRRO / CIDADE / UF (OPCIONAL)"
            placeholder="Complemento, bairro, cidade/UF"
            error={errors.complementoBairroCidadeUf?.message}
            {...register('complementoBairroCidadeUf')}
          />

          {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}

          <div className={styles.footer}>
            <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
            <Button type="submit" variant="primary" size="md" isLoading={isLoading}>
              {local ? 'Salvar alterações' : 'Cadastrar local'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
