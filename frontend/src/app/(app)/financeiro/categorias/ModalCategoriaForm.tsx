'use client'

import { useEffect } from 'react'
import { clsx } from 'clsx'
import { X, ArrowDownCircle, ArrowUpCircle, ArrowLeftRight, AlertTriangle } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useCategoriaForm } from '@/hooks/financeiro/categoria/useCategoriaForm'
import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import type { CategoriaResponse, TipoCategoria } from '@/types/financeiro/categoria.type'
import styles from './ModalCategoriaForm.module.css'

const OPCOES_TIPO: { valor: TipoCategoria; label: string; descricao: string; Icone: typeof ArrowDownCircle }[] = [
  { valor: 'ENTRADA', label: 'Entrada', descricao: 'Dízimos, ofertas, doações', Icone: ArrowDownCircle },
  { valor: 'SAIDA', label: 'Saída', descricao: 'Contas, manutenção, salários', Icone: ArrowUpCircle },
  { valor: 'AMBOS', label: 'Ambos', descricao: 'Aceita os dois tipos', Icone: ArrowLeftRight },
]

interface ModalCategoriaFormProps {
  categoria?: CategoriaResponse   // presente = edição
  onClose: () => void
}

export function ModalCategoriaForm({ categoria, onClose }: ModalCategoriaFormProps) {
  const {
    register, handleSubmit, setValue, watch,
    formState: { errors },
    onSubmit, erroGeral, isLoading, isFormIncomplete, ehEdicao,
    confirmacaoPendente, confirmarAtualizacao, cancelarConfirmacao,
  } = useCategoriaForm({
    categoriaId: categoria?.id,
    categoriaInicial: categoria,
    onSuccess: onClose,
  })

  const tipoSelecionado = watch('tipo')
  const { saindo, fechar } = useFecharAnimado(onClose, 240)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) fechar()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [fechar, isLoading])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !isLoading && fechar()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <button type="button" className={styles.btnClose} onClick={fechar} aria-label="Fechar">
          <X size={20} />
        </button>

        <div className={styles.header}>
          <h2 className={styles.titulo}>{ehEdicao ? 'Editar categoria' : 'Nova categoria'}</h2>
          <p className={styles.subtitulo}>Defina o nome e o tipo da classificação.</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
          {/* Nome */}
          <div className={styles.campo}>
            <label className={styles.label} htmlFor="nome">NOME DA CATEGORIA</label>
            <input
              id="nome"
              className={styles.input}
              placeholder="Ex: Dízimos e Ofertas"
              {...register('nome')}
            />
            {errors.nome && <span className={styles.erroCampo}>{errors.nome.message}</span>}
          </div>

          {/* Tipo — três cards selecionáveis */}
          <div className={styles.campo}>
            <span className={styles.label}>TIPO DE MOVIMENTAÇÃO</span>
            <div className={styles.opcoes}>
              {OPCOES_TIPO.map(({ valor, label, descricao, Icone }) => {
                const ativo = tipoSelecionado === valor
                return (
                  <button
                    key={valor}
                    type="button"
                    className={`${styles.opcao} ${ativo ? styles.opcaoAtiva : ''} ${ativo ? styles[valor.toLowerCase()] : ''}`}
                    onClick={() => setValue('tipo', valor, { shouldValidate: true, shouldDirty: true })}
                  >
                    <span className={styles.opcaoIcone}><Icone size={22} /></span>
                    <span className={styles.opcaoLabel}>{label}</span>
                    <span className={styles.opcaoDesc}>{descricao}</span>
                  </button>
                )
              })}
            </div>
            {errors.tipo && <span className={styles.erroCampo}>{errors.tipo.message}</span>}
          </div>

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

          <div className={styles.rodape}>
            <button type="button" className={styles.btnCancelar} onClick={fechar} disabled={isLoading}>
              Cancelar
            </button>
            <button type="submit" className={styles.btnSalvar} disabled={isFormIncomplete || isLoading}>
              {isLoading ? 'Salvando…' : 'Salvar categoria'}
            </button>
          </div>
        </form>
      </div>

      {/*
        A11/rodada 3: confirmação leve (não a crítica com nome digitado) — a categoria já
        tem lançamentos associados e a mudança vale para todos eles.
      */}
      {confirmacaoPendente && (
        <ModalArquivar
          titulo="Categoria em uso"
          mensagem={
            <>
              Esta categoria está associada a{' '}
              <strong>
                {confirmacaoPendente.totalMovimentacoes}{' '}
                {confirmacaoPendente.totalMovimentacoes === 1 ? 'lançamento' : 'lançamentos'}
              </strong>
              . A mudança vale para todos eles. Deseja continuar?
            </>
          }
          icone={AlertTriangle}
          reversivel={false}
          textoConfirmar="Salvar mesmo assim"
          textoCarregando="Salvando…"
          isLoading={isLoading}
          erro={erroGeral}
          onConfirmar={confirmarAtualizacao}
          onClose={cancelarConfirmacao}
        />
      )}
    </div>
  )
}