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
      // `LocalEventoResponse.endereco` só devolve o texto do CEP/logradouro/número quando o
      // local tem endereço PRÓPRIO — herdado da igreja não deve ser reaproveitado (submeter
      // sem editar viraria "endereço próprio" igual ao da igreja, quando a intenção era
      // continuar herdando). O `complementoBairroCidadeUf` gravado não volta na resposta (o
      // DTO não o expõe) — ao editar um local que já tinha complemento, o campo some da tela
      // e precisa ser redigitado para não ser perdido no salvar.
      cepLogradouroNumero: local && !local.enderecoHerdado ? local.endereco ?? '' : '',
      complementoBairroCidadeUf: '',
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
          {/* A API não devolve o complemento salvo (só o CEP/logradouro/número) — ao editar,
              o campo sempre reabre vazio e precisa ser redigitado para não se perder. */}
          {local && <p className={styles.avisoComplemento}>Se este local já tinha complemento, digite de novo — o campo não é reidratado ao editar.</p>}

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
