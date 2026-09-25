'use client'

import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { Button } from '@/components/common/button/Button'
import { BeneficiarioField } from '@/components/financeiro/comum/BeneficiarioField'
import { UploadAnexo } from '@/components/anexo/UploadAnexo'
import { Colapsavel } from '@/components/common/Transicao/Colapsavel'
import { useCategoriasSelect } from '@/hooks/financeiro/categoria/useCategoriaSelect'
import { notificar } from '@/components/common/Notificacao/notificar'
import { formatarValorDigitado } from '@/lib/formats/financeiro/movimentacaoFormat'
import type {
  ContaRequest,
  ContaResponse,
  RecorrenciaTipo,
  AnexoUploadResponse,
} from '@/types/contas-a-pagar'
import { contaSchema } from '@/lib/financeiro/contas-a-pagar/schemas'
import type { ContaFormValues } from '@/lib/financeiro/contas-a-pagar/schemas'
import styles from './ModalContaForm.module.css'

interface Props {
  aberto: boolean
  conta?: ContaResponse | null
  onClose: () => void
  onSubmit: (req: ContaRequest) => Promise<void>
}

const RECORRENCIA_OPCOES: { value: RecorrenciaTipo; label: string }[] = [
  { value: 'SEM_RECORRENCIA', label: 'Não se repete' },
  { value: 'SEMANAL', label: 'Semanal' },
  { value: 'QUINZENAL', label: 'Quinzenal' },
  { value: 'MENSAL', label: 'Mensal' },
  { value: 'BIMESTRAL', label: 'Bimestral' },
  { value: 'TRIMESTRAL', label: 'Trimestral' },
  { value: 'SEMESTRAL', label: 'Semestral' },
  { value: 'ANUAL', label: 'Anual' },
]

export function ModalContaForm({ aberto, conta, onClose, onSubmit }: Props) {
  const { saindo: estaSaindo, fechar: aoSair } = useFecharAnimado(onClose, 220)
  const { data: categorias } = useCategoriasSelect()

  const [temRecorrencia, setTemRecorrencia] = useState(
    conta?.recorrencia?.tipo !== undefined && conta?.recorrencia?.tipo !== 'SEM_RECORRENCIA',
  )
  const [anexo, setAnexo] = useState<AnexoUploadResponse | null>(null)
  const [anexoId, setAnexoId] = useState<string | null>(null)

  const { register, handleSubmit, setValue, watch, reset, formState: { errors } } = useForm<ContaFormValues>({
    resolver: zodResolver(contaSchema),
    defaultValues: {
      descricao: conta?.descricao ?? '',
      valor: conta?.valor ?? '',
      vencimento: conta?.vencimento ?? '',
      beneficiario: {
        pessoaId: conta?.beneficiario.pessoaId ?? null,
        texto: conta?.beneficiario.texto ?? '',
      },
      categoriaId: conta?.categoriaId ?? null,
      recorrencia: {
        tipo: conta?.recorrencia?.tipo ?? 'SEM_RECORRENCIA',
        parcelaAtual: conta?.recorrencia?.parcelaAtual ?? undefined,
        totalParcelas: conta?.recorrencia?.totalParcelas ?? undefined,
      },
      observacoes: conta?.observacoes ?? '',
      anexoId: conta?.anexoId ?? null,
    },
  })

  useEffect(() => {
    if (aberto) {
      reset({
        descricao: conta?.descricao ?? '',
        valor: conta?.valor ?? '',
        vencimento: conta?.vencimento ?? '',
        beneficiario: {
          pessoaId: conta?.beneficiario.pessoaId ?? null,
          texto: conta?.beneficiario.texto ?? '',
        },
        categoriaId: conta?.categoriaId ?? null,
        recorrencia: {
          tipo: conta?.recorrencia?.tipo ?? 'SEM_RECORRENCIA',
          parcelaAtual: conta?.recorrencia?.parcelaAtual ?? undefined,
          totalParcelas: conta?.recorrencia?.totalParcelas ?? undefined,
        },
        observacoes: conta?.observacoes ?? '',
        anexoId: conta?.anexoId ?? null,
      })
      setAnexoId(conta?.anexoId ?? null)
      setTemRecorrencia(
        conta?.recorrencia?.tipo !== undefined && conta?.recorrencia?.tipo !== 'SEM_RECORRENCIA',
      )
    }
  }, [aberto, conta, reset])

  useEffect(() => {
    if (aberto) document.body.style.overflow = 'hidden'
    else document.body.style.overflow = ''
    return () => { document.body.style.overflow = '' }
  }, [aberto])

  async function aoSubmeter(dados: ContaFormValues) {
    const req: ContaRequest = {
      descricao: dados.descricao || null,
      valor: dados.valor,
      vencimento: dados.vencimento,
      beneficiario: dados.beneficiario,
      categoriaId: dados.categoriaId || null,
      competencia: undefined,
      documentoNumero: undefined,
      recorrencia: temRecorrencia
        ? {
            tipo: dados.recorrencia.tipo,
            parcelaAtual: dados.recorrencia.parcelaAtual,
            totalParcelas: dados.recorrencia.totalParcelas,
          }
        : null,
      observacoes: dados.observacoes || null,
      anexoId: anexoId ?? null,
    }
    try {
      await onSubmit(req)
      notificar.sucesso(conta ? 'Conta atualizada' : 'Conta cadastrada')
    } catch {
      notificar.erro('Erro ao salvar', 'Tente novamente.')
    }
  }

  const valorWatch = watch('valor')

  if (!aberto && !estaSaindo) return null

  return (
    <div
      className={`${styles.overlayer} ${estaSaindo ? styles.saindo : ''}`}
      onClick={(e) => { if (e.target === e.currentTarget) aoSair() }}
      role="dialog"
      aria-modal="true"
      aria-label={conta ? 'Editar conta a pagar' : 'Nova conta a pagar'}
    >
      <div className={styles.modal}>
        <div className={styles.header}>
          <h2 className={styles.titulo}>
            {conta ? 'Editar conta' : 'Nova conta a pagar'}
          </h2>
          <button className={styles.btnFechar} onClick={aoSair} aria-label="Fechar">
            ×
          </button>
        </div>

        <form
          id="conta-form"
          className={styles.form}
          onSubmit={handleSubmit(aoSubmeter)}
          noValidate
        >
          <div className={styles.grid}>
            {/* Descrição */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="descricao">Descrição</label>
              <input
                id="descricao"
                type="text"
                className={`${styles.input} ${errors.descricao ? styles.inputErro : ''}`}
                placeholder="Ex: Aluguel do salão, Conta de luz..."
                {...register('descricao')}
              />
              {errors.descricao && (
                <span className={styles.erro}>{errors.descricao.message}</span>
              )}
            </div>

            {/* Beneficiário */}
            <BeneficiarioField
              value={watch('beneficiario')}
              onChange={(v) => setValue('beneficiario', v, { shouldValidate: true })}
              erroPessoa={errors.beneficiario?.pessoaId?.message}
              erroTexto={errors.beneficiario?.texto?.message}
            />

            {/* Valor */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="valor">Valor (R$)</label>
              <input
                id="valor"
                type="text"
                inputMode="numeric"
                className={`${styles.input} ${errors.valor ? styles.inputErro : ''}`}
                placeholder="R$ 0,00"
                value={formatarValorDigitado(valorWatch)}
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
            </div>

            {/* Vencimento */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="vencimento">Vencimento</label>
              <CampoData
                id="vencimento"
                value={watch('vencimento')}
                onChange={(v) => setValue('vencimento', v, { shouldValidate: true })}
                erro={errors.vencimento?.message}
              />
            </div>

            {/* Categoria */}
            <div className={styles.campo}>
              <label className={styles.label} htmlFor="categoria">Categoria</label>
              <select
                id="categoria"
                className={styles.select}
                {...register('categoriaId')}
              >
                <option value="">Selecione</option>
                {categorias?.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.nome}</option>
                ))}
              </select>
            </div>

            {/* Observações */}
            <div className={`${styles.campo} ${styles.full}`}>
              <label className={styles.label} htmlFor="observacoes">Observações</label>
              <textarea
                id="observacoes"
                className={styles.textarea}
                placeholder="Informações adicionais..."
                rows={2}
                {...register('observacoes')}
              />
            </div>
          </div>

          {/* Recorrência toggle */}
          <div className={styles.secao}>
            <label className={styles.toggleLinha}>
              <span className={styles.toggleTexto}>Repetir</span>
              <button
                type="button"
                className={`${styles.toggle} ${temRecorrencia ? styles.toggleOn : ''}`}
                onClick={() => setTemRecorrencia((v) => !v)}
                aria-pressed={temRecorrencia}
              >
                <span className={styles.toggleThumb} />
              </button>
            </label>

            <Colapsavel aberto={temRecorrencia}>
              <div className={styles.grid}>
                <div className={styles.campo}>
                  <label className={styles.label} htmlFor="recorrencia">Frequência</label>
                  <select
                    id="recorrencia"
                    className={styles.select}
                    {...register('recorrencia.tipo')}
                  >
                    {RECORRENCIA_OPCOES.map((o) => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                </div>

                <div className={styles.campo}>
                  <label className={styles.label} htmlFor="parcelaAtual">Parcela atual</label>
                  <input
                    id="parcelaAtual"
                    type="number"
                    min="1"
                    className={styles.input}
                    {...register('recorrencia.parcelaAtual')}
                  />
                </div>

                <div className={styles.campo}>
                  <label className={styles.label} htmlFor="totalParcelas">Total de parcelas</label>
                  <input
                    id="totalParcelas"
                    type="number"
                    min="1"
                    className={styles.input}
                    placeholder="Em branco = sem fim"
                    {...register('recorrencia.totalParcelas')}
                  />
                </div>
              </div>
            </Colapsavel>
          </div>

          {/* Anexo */}
          <div className={styles.secao}>
            <label className={styles.label}>Anexo</label>
            <UploadAnexo
              valor={anexoId}
              anexo={anexo}
              onChange={(id) => setAnexoId(id)}
              onAnexoChange={(a) => setAnexo(a)}
            />
          </div>
        </form>

        <div className={styles.footer}>
          <Button variant="ghost" onClick={aoSair}>Cancelar</Button>
          <Button
            type="submit"
            form="conta-form"
            variant="primary"
          >
            {conta ? 'Salvar alterações' : 'Cadastrar'}
          </Button>
        </div>
      </div>
    </div>
  )
}
