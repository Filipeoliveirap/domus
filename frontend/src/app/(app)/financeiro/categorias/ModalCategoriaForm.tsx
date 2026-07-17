'use client'

import { useEffect } from 'react'
import { X, ArrowDownCircle, ArrowUpCircle, ArrowLeftRight } from 'lucide-react'
import { useCategoriaForm } from '@/hooks/financeiro/categoria/useCategoriaForm'
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
  } = useCategoriaForm({
    categoriaId: categoria?.id,
    categoriaInicial: categoria,
    onSuccess: onClose,
  })

  const tipoSelecionado = watch('tipo')

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose, isLoading])

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
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
            <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={isLoading}>
              Cancelar
            </button>
            <button type="submit" className={styles.btnSalvar} disabled={isFormIncomplete || isLoading}>
              {isLoading ? 'Salvando…' : 'Salvar categoria'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}