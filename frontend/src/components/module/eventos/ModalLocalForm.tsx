'use client'

import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { MapPin, X, Landmark } from 'lucide-react'
import { clsx } from 'clsx'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useLocalEventoForm } from '@/hooks/evento/useLocalEventoForm'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { enderecoIgrejaParaCamposCompactos, jaExisteEnderecoDaIgreja } from '@/lib/formats/endereco'
import { localEventoSchema, type LocalEventoFormData, type LocalEventoFormInput } from '@/lib/validators'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import type { LocalEventoRequest, LocalEventoResponse } from '@/types/evento.type'
import styles from './ModalLocalForm.module.css'

interface Props {
  /** Presente = edição; ausente = criação. */
  local: LocalEventoResponse | null
  onClose: () => void
  /** Chamado com o endereço recém-criado (só no modo criação, tela de Endereços), antes do onClose. */
  onCriado?: (local: LocalEventoResponse) => void
  /** Quando presente (aberto pelo formulário de evento): NÃO persiste. Só devolve o payload
   *  para o evento "segurar" — o cadastro de verdade acontece quando o evento é salvo. */
  onDefinir?: (payload: LocalEventoRequest) => void
  /** Reidrata os campos ao reabrir um endereço pendente (modo onDefinir) para editar. */
  valoresIniciais?: LocalEventoRequest | null
}

export function ModalLocalForm({ local, onClose, onCriado, onDefinir, valoresIniciais }: Props) {
  const { saindo, fechar } = useFecharAnimado(onClose, 260)
  const { salvar, isLoading, erroGeral } = useLocalEventoForm(local, fechar, onCriado)
  const { data: igreja } = useMinhaIgreja()
  const { data: locais = [] } = useLocaisEvento()

  const modoDefinir = !!onDefinir
  const editando = !!local || !!valoresIniciais
  const mostrarUsarIgreja = !editando && !!igreja?.endereco
    && !jaExisteEnderecoDaIgreja(locais, igreja.endereco)

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<LocalEventoFormInput, unknown, LocalEventoFormData>({
    resolver: zodResolver(localEventoSchema),
    defaultValues: {
      nome: valoresIniciais?.nome ?? local?.nome ?? '',
      capacidade: valoresIniciais?.capacidade ?? local?.capacidade ?? undefined,
      // Reidrata dos campos CRUS (não do `endereco` formatado, que colapsa tudo num texto).
      cepLogradouroNumero: valoresIniciais?.cepLogradouroNumero ?? local?.cepLogradouroNumero ?? '',
      complementoBairroCidadeUf: valoresIniciais?.complementoBairroCidadeUf ?? local?.complementoBairroCidadeUf ?? '',
    },
  })

  // Trava o scroll do fundo enquanto o modal está aberto (padrão dos outros modais).
  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !isLoading) fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, isLoading])

  const onSubmit = (data: LocalEventoFormData) => {
    const payload: LocalEventoRequest = {
      nome: data.nome,
      capacidade: data.capacidade ?? null,
      cepLogradouroNumero: data.cepLogradouroNumero || null,
      complementoBairroCidadeUf: data.complementoBairroCidadeUf || null,
    }
    if (modoDefinir) {
      onDefinir!(payload)
      fechar()
      return
    }
    salvar(payload)
  }

  if (typeof document === 'undefined') return null

  return createPortal(
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      onMouseDown={() => !isLoading && fechar()}
    >
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <div className={styles.header}>
          <div className={styles.iconBox}>
            <MapPin size={24} />
          </div>
          <button type="button" className={styles.btnClose} onClick={fechar} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.intro}>
          <h2 className={styles.title}>{editando ? 'Editar endereço' : 'Novo endereço'}</h2>
        </div>

        {/* stopPropagation: o modal está num portal, mas o submit ainda borbulha pela árvore
            React até o <form> do EventoForm. Sem isto, salvar aqui dispara o submit do evento. */}
        <form className={styles.form} onSubmit={(e) => { e.stopPropagation(); void handleSubmit(onSubmit)(e) }}>
          <Input
            id="local-nome"
            label="NOME"
            placeholder="Ex: Santuário Principal"
            error={errors.nome?.message}
            {...register('nome')}
          />

          {mostrarUsarIgreja && (
            <button
              type="button"
              className={styles.btnUsarIgreja}
              onClick={() => {
                const { linha1, linha2 } = enderecoIgrejaParaCamposCompactos(igreja.endereco!)
                setValue('cepLogradouroNumero', linha1, { shouldDirty: true })
                setValue('complementoBairroCidadeUf', linha2, { shouldDirty: true })
              }}
            >
              <Landmark size={16} aria-hidden="true" />
              Usar o endereço da igreja
            </button>
          )}

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
            <button type="button" className={styles.btnCancel} onClick={fechar}>Cancelar</button>
            <Button type="submit" variant="primary" size="md" isLoading={isLoading}>
              {modoDefinir ? 'Salvar' : local ? 'Salvar alterações' : 'Cadastrar endereço'}
            </Button>
          </div>
        </form>
      </div>
    </div>,
    document.body,
  )
}
