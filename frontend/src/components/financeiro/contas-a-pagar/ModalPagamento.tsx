'use client'

import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { formatarMoeda, formatarValorDigitado } from '@/lib/formats/financeiro/movimentacaoFormat'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { Button } from '@/components/common/button/Button'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { ContaResponse, RegistrarPagamentoRequest, FormaPagamento } from '@/types/contas-a-pagar'
import { pagamentoSchema } from '@/lib/financeiro/contas-a-pagar/schemas'
import type { PagamentoFormValues } from '@/lib/financeiro/contas-a-pagar/schemas'
import styles from './ModalPagamento.module.css'

interface Props {
  aberto: boolean
  conta: ContaResponse | null
  onClose: () => void
  onSubmit: (req: RegistrarPagamentoRequest) => Promise<void>
}

const MEIO_OPCOES: { value: FormaPagamento; label: string }[] = [
  { value: 'PIX', label: 'Pix' },
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'TRANSFERENCIA', label: 'Transferência' },
  { value: 'BOLETO', label: 'Boleto' },
  { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
  { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
  { value: 'OUTRO', label: 'Outro' },
]

export function ModalPagamento({ aberto, conta, onClose, onSubmit }: Props) {
  const { saindo: estaSaindo, fechar: aoSair } = useFecharAnimado(onClose, 200)

  const { register, handleSubmit, setValue, reset, watch, formState: { errors } } = useForm<PagamentoFormValues>({
    resolver: zodResolver(pagamentoSchema),
    defaultValues: {
      valor: '',
      data: '',
      meioPagamento: 'PIX',
      observacao: '',
    },
  })

  const valorDebitado = watch('valor')
  const restante = conta ? (parseFloat(conta.valor) - parseFloat(conta.valorPago)) : 0
  const valorDigitado = parseFloat(valorDebitado) || 0
  const totalDebitado = Math.min(valorDigitado, restante)

  useEffect(() => {
    if (aberto && conta) {
      const restanteCalc = parseFloat(conta.valor) - parseFloat(conta.valorPago)
      const hoje = new Date().toISOString().split('T')[0]
      reset({
        valor: restanteCalc.toFixed(2),
        data: hoje,
        meioPagamento: 'PIX',
        observacao: '',
      })
    }
  }, [aberto, conta, reset])

  useEffect(() => {
    if (aberto) document.body.style.overflow = 'hidden'
    else document.body.style.overflow = ''
    return () => { document.body.style.overflow = '' }
  }, [aberto])

  async function aoSubmeter(dados: PagamentoFormValues) {
    if (!conta) return
    const valorNumerico = parseFloat(dados.valor)
    const restanteCalc = parseFloat(conta.valor) - parseFloat(conta.valorPago)
    const valorFinal = valorNumerico > restanteCalc ? restanteCalc : valorNumerico
    const req: RegistrarPagamentoRequest = {
      valor: valorFinal.toFixed(2),
      data: dados.data,
      meioPagamento: dados.meioPagamento,
      observacao: dados.observacao || undefined,
    }
    try {
      await onSubmit(req)
      notificar.sucesso('Pagamento registrado')
    } catch {
      notificar.erro('Erro ao registrar pagamento')
    }
  }

  if (!aberto && !estaSaindo) return null

  return (
    <div
      className={`${styles.overlayer} ${estaSaindo ? styles.saindo : ''}`}
      onClick={(e) => { if (e.target === e.currentTarget) aoSair() }}
      role="dialog"
      aria-modal="true"
      aria-label="Registrar pagamento"
    >
      <div className={styles.modal}>
        <div className={styles.header}>
          <h2 className={styles.titulo}>Registrar pagamento</h2>
          <button className={styles.btnFechar} onClick={aoSair} aria-label="Fechar">
            ×
          </button>
        </div>

        {conta && (
          <div className={styles.contaInfo}>
            <span className={styles.contaDesc}>{conta.descricao || 'Conta sem descrição'}</span>
            <div className={styles.contaValores}>
              <span>
                <span className={styles.valorLabel}>Total: </span>
                <span className={styles.valorDestaque}>{formatarMoeda(conta.valor)}</span>
              </span>
              {conta.status === 'PARCIAL' && (
                <span>
                  <span className={styles.valorLabel}>Já pago: </span>
                  <span className={styles.valorJaPago}>{formatarMoeda(conta.valorPago)}</span>
                </span>
              )}
              <span>
                <span className={styles.valorLabel}>Restante: </span>
                <span className={styles.valorRestante}>{formatarMoeda(restante)}</span>
              </span>
            </div>
          </div>
        )}

        <form
          id="pagamento-form"
          className={styles.form}
          onSubmit={handleSubmit(aoSubmeter)}
          noValidate
        >
          <div className={styles.campos}>
            {/* Valor */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="pgt-valor">Valor (R$)</label>
              <input
                id="pgt-valor"
                type="text"
                inputMode="numeric"
                className={`${styles.input} ${errors.valor ? styles.inputErro : ''}`}
                placeholder="R$ 0,00"
                value={formatarValorDigitado(valorDebitado)}
                onChange={(e) => {
                  const digitos = e.target.value.replace(/\D/g, '')
                  const centavos = parseInt(digitos || '0', 10)
                  const emReais = (centavos / 100).toFixed(2)
                  setValue('valor', digitos === '' ? '' : emReais, { shouldValidate: true, shouldDirty: true })
                }}
              />
              {errors.valor && (
                <span className={styles.erro}>{errors.valor.message}</span>
              )}
              <div className={styles.totalDebitado}>
                Total debitado: <strong>{formatarMoeda(totalDebitado)}</strong>
              </div>
            </div>

            {/* Data */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="pgt-data">Data do pagamento</label>
              <CampoData
                id="pgt-data"
                value={watch('data')}
                onChange={(v) => setValue('data', v, { shouldValidate: true })}
                erro={errors.data?.message}
              />
            </div>

            {/* Meio de pagamento */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="pgt-meio">Meio de pagamento</label>
              <select
                id="pgt-meio"
                className={styles.select}
                {...register('meioPagamento')}
              >
                {MEIO_OPCOES.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </div>

            {/* Observação */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="pgt-obs">Observação</label>
              <input
                id="pgt-obs"
                type="text"
                className={styles.input}
                placeholder="Opcional"
                {...register('observacao')}
              />
            </div>
          </div>
        </form>

        <div className={styles.footer}>
          <Button variant="ghost" onClick={aoSair}>Cancelar</Button>
          <Button
            type="submit"
            form="pagamento-form"
            variant="primary"
          >
            Confirmar pagamento
          </Button>
        </div>
      </div>
    </div>
  )
}
