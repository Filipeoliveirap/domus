'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import { useQueryClient } from '@tanstack/react-query'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useCelulaForm } from '@/hooks/celula/useCelulaForm'
import { useAtualizarFotoCelula } from '@/hooks/celula/useAtualizarFotoCelula'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { Button } from '@/components/common/button/Button'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { CelulaResponse, CelulaRequest } from '@/types/celula.type'
import styles from './ModalCelulaForm.module.css'

const DIA_OPTIONS = [
  { value: '', label: 'Sem dia fixo' },
  { value: 'SEGUNDA', label: 'Segunda' },
  { value: 'TERCA', label: 'Terça' },
  { value: 'QUARTA', label: 'Quarta' },
  { value: 'QUINTA', label: 'Quinta' },
  { value: 'SEXTA', label: 'Sexta' },
  { value: 'SABADO', label: 'Sábado' },
  { value: 'DOMINGO', label: 'Domingo' },
]

// Só os campos que o form usa — casa CelulaResponse (lista) e CelulaDetalheResponse ([id]).
type CelulaEditavel = Pick<CelulaResponse, 'id' | 'nome' | 'fotoId' | 'diaSemana' | 'horario'>

interface Props {
  // `null` = criar; objeto = editar. Mesma convenção do ModalMinisterioForm.
  celula: CelulaEditavel | null
  onClose: () => void
}

export function ModalCelulaForm({ celula, onClose }: Props) {
  const { celula: rotulo } = useRotulos()
  const queryClient = useQueryClient()
  const [fotoId, setFotoId] = useState<string | null>(celula?.fotoId ?? null)
  const [salvando, setSalvando] = useState(false)

  const form = useCelulaForm({ celulaId: celula?.id, celulaInicial: celula ?? undefined })
  const atualizarFoto = useAtualizarFotoCelula(celula?.id)
  const { register, handleSubmit, setValue, watch, formState: { errors }, isFormIncomplete } = form
  const horarioValue = (watch('horario') as string) ?? ''

  const { saindo, fechar } = useFecharAnimado(onClose, 260)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !salvando) fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, salvando])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  async function salvar() {
    const data = form.getValues()
    setSalvando(true)
    try {
      const payload: CelulaRequest = {
        nome: data.nome as string,
        diaSemana: ((data.diaSemana as string) || undefined) as CelulaRequest['diaSemana'],
        horario: data.horario ? (data.horario as string) + ':00' : undefined,
        fotoId: fotoId ?? undefined,
      }
      if (celula) await celulaService.atualizar(celula.id, payload)
      else await celulaService.criar(payload)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(celula ? `${rotulo.singular} atualizada!` : `${rotulo.singular} criada!`)
      onClose()
    } catch {
      notificar.erro(`Erro ao salvar ${rotulo.singular.toLowerCase()}.`)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !salvando && fechar()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <h2 className={styles.titulo}>
          {celula ? `Editar ${rotulo.singular.toLowerCase()}` : `Nova ${rotulo.singular.toLowerCase()}`}
        </h2>
        <form onSubmit={handleSubmit(salvar)} className={styles.form}>
          <div className={styles.fotoWrap}>
            <UploadFoto
              valor={fotoId}
              onChange={(id) => {
                setFotoId(id)
                // Em criação, a célula ainda não existe (sem id) — a foto vai junto do "Salvar".
                // Em edição, salva sozinha ao confirmar o recorte.
                if (!celula) return
                const fotoAnterior = fotoId
                atualizarFoto.mutate(id, {
                  onSuccess: () => notificar.sucesso(id ? 'Foto atualizada.' : 'Foto removida.'),
                  onError: (erro: unknown) => {
                    setFotoId(fotoAnterior)
                    const mensagem =
                      (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                      'Tente novamente em alguns instantes.'
                    notificar.erro('Não foi possível salvar a foto', mensagem)
                  },
                })
              }}
              formato="circulo"
              nomeFallback={form.getValues('nome') as string}
            />
          </div>
          <Input id="nome-celula" label="NOME*" placeholder={`Nome da ${rotulo.singular.toLowerCase()}`}
            error={errors.nome?.message} {...register('nome')} />
          <Select id="dia-celula" label={`DIA QUE A ${rotulo.singular.toUpperCase()} OCORRE`} placeholder="Selecione"
            options={DIA_OPTIONS} error={errors.diaSemana?.message} {...register('diaSemana')} />
          <Input id="horario-celula" label="HORÁRIO DE INÍCIO" placeholder="hh:mm" inputMode="numeric" maxLength={5}
            value={horarioValue} onChange={(e) => {
              const digits = e.target.value.replace(/\D/g, '').slice(0, 4)
              const formatted = digits.length <= 2 ? digits : digits.replace(/(\d{2})(\d{0,2})/, '$1:$2')
              setValue('horario', formatted, { shouldValidate: true })
            }} error={errors.horario?.message} />
          {form.erroGeral && <p className={styles.erro}>{form.erroGeral}</p>}
          <div className={styles.acoes}>
            <Button type="button" variant="secondary" onClick={fechar} disabled={salvando}>
              Cancelar
            </Button>
            <Button type="submit" variant="primary" isLoading={salvando}
              disabled={isFormIncomplete || salvando}>
              {celula ? 'Salvar' : `Criar ${rotulo.singular.toLowerCase()}`}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
