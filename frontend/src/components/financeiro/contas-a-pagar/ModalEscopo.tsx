'use client'

import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/common/button/Button'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import type { EscopoEdicaoSerie } from '@/types/contas-a-pagar'
import styles from './ModalEscopo.module.css'

interface Props {
  aberto: boolean
  titulo: string
  acao: 'editar' | 'excluir'
  onClose: () => void
  onConfirmar: (escopo: EscopoEdicaoSerie) => void
  carregando?: boolean
  temPagamentos?: boolean
}

export function ModalEscopo({
  aberto,
  titulo,
  acao,
  onClose,
  onConfirmar,
  carregando,
  temPagamentos,
}: Props) {
  const { saindo: estaSaindo, fechar: aoSair } = useFecharAnimado(onClose, 200)

  if (!aberto && !estaSaindo) return null

  return (
    <div
      className={`${styles.overlayer} ${estaSaindo ? styles.saindo : ''}`}
      onClick={(e) => { if (e.target === e.currentTarget) aoSair() }}
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
    >
      <div className={styles.modal}>
        <div className={styles.header}>
          <div className={styles.headerLeft}>
            <AlertTriangle size={20} className={styles.iconWarn} aria-hidden="true" />
            <h2 className={styles.titulo}>{titulo}</h2>
          </div>
          <button className={styles.btnFechar} onClick={aoSair} aria-label="Fechar">×</button>
        </div>

        <div className={styles.body}>
          <p className={styles.desc}>
            Esta é uma conta recorrente. Escolha o escopo da ação:
          </p>

          <div className={styles.opcoes}>
            <button
              type="button"
              className={styles.opcao}
              onClick={() => onConfirmar('ESTA')}
              disabled={carregando}
            >
              <strong>Esta conta</strong>
              <span>{acao === 'editar' ? 'Edita apenas esta parcela.' : 'Exclui apenas esta parcela.'}</span>
            </button>

            {acao === 'editar' && (
              <button
                type="button"
                className={`${styles.opcao} ${styles.opcaoPerigo}`}
                onClick={() => onConfirmar('ESTA_E_SEGUINTES')}
                disabled={carregando}
              >
                <strong>Esta e as futuras</strong>
                <span>Esta parcela e as próximas.</span>
              </button>
            )}

            <button
              type="button"
              className={`${styles.opcao} ${styles.opcaoPerigo}`}
              onClick={() => onConfirmar('SERIE')}
              disabled={carregando}
            >
              <strong>Toda a série</strong>
              <span>
                {acao === 'editar'
                  ? 'Edita esta e todas as parcelas passadas e futuras.'
                  : 'Exclui esta e todas as parcelas passadas e futuras.'}
              </span>
              {temPagamentos && (
                <span className={styles.alertaPagamentos}>
                  Existem pagamentos registrados — cuidado!
                </span>
              )}
            </button>
          </div>
        </div>

        <div className={styles.footer}>
          <Button variant="ghost" onClick={aoSair} disabled={carregando}>
            Cancelar
          </Button>
        </div>
      </div>
    </div>
  )
}
