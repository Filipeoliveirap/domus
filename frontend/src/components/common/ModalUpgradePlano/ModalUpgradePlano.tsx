'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Sparkles, X, ArrowRight, ShieldAlert } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import styles from './ModalUpgradePlano.module.css'

export interface ModalUpgradePlanoProps {
  aberto: boolean
  aoFechar: () => void
  titulo?: string
  descricao?: string
  planoAtual?: string
  planoSugerido?: string
  progressoCurrent?: number
  progressoMax?: number
}

export function ModalUpgradePlano({
  aberto,
  aoFechar,
  titulo = 'Limite do Plano Atingido',
  descricao = 'Sua igreja atingiu a capacidade máxima de registros permitida pelo seu plano atual. Faça upgrade para continuar expandindo.',
  planoAtual = 'Básico',
  planoSugerido = 'Pro',
  progressoCurrent,
  progressoMax,
}: ModalUpgradePlanoProps) {
  const router = useRouter()
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)

  useEffect(() => {
    if (!aberto) return
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aberto, fechar])

  if (!aberto) return null

  const porcentagem = progressoCurrent != null && progressoMax != null && progressoMax > 0
    ? Math.min(100, Math.round((progressoCurrent / progressoMax) * 100))
    : 100

  const irParaPlanos = () => {
    fechar()
    router.push('/configuracoes/assinatura')
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-upgrade-modal"
      >
        <span className={styles.grabber} aria-hidden="true" />

        <div className={styles.modalHeader}>
          <div className={styles.headerInfo}>
            <div className={styles.iconeBadge}>
              <Sparkles size={22} />
            </div>
            <h2 className={styles.modalTitulo} id="titulo-upgrade-modal">
              {titulo}
            </h2>
          </div>
          <button
            type="button"
            className={styles.modalFechar}
            onClick={fechar}
            aria-label="Fechar"
          >
            <X size={18} />
          </button>
        </div>

        <div className={styles.modalCorpo}>
          <p className={styles.descricao}>{descricao}</p>

          {progressoCurrent != null && progressoMax != null && (
            <div className={styles.progressoBox}>
              <div className={styles.progressoTopo}>
                <span>Uso do Plano {planoAtual}</span>
                <span>{progressoCurrent} de {progressoMax}</span>
              </div>
              <div className={styles.trilhoBarra}>
                <div
                  className={styles.preenchimentoBarra}
                  style={{ width: `${porcentagem}%` }}
                />
              </div>
            </div>
          )}
        </div>

        <div className={styles.modalRodape}>
          <button type="button" className={styles.btnCancelar} onClick={fechar}>
            Agora não
          </button>
          <button type="button" className={styles.btnUpgrade} onClick={irParaPlanos}>
            <span>Fazer Upgrade para {planoSugerido}</span>
            <ArrowRight size={16} />
          </button>
        </div>
      </div>
    </div>
  )
}
