'use client'

import React, { useEffect } from 'react'
import Link from 'next/link'
import { AlertTriangle, Lock, X, ArrowRight } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarAssinatura } from '@/lib/permissoes'
import styles from './ModalLimiteExcedido.module.css'

interface ModalLimiteExcedidoProps {
  aberto: boolean
  onFechar: () => void
  limite?: number
}

export function ModalLimiteExcedido({ aberto, onFechar, limite }: ModalLimiteExcedidoProps) {
  const { congregacao } = useRotulos()
  const igrejaMaeId = useAuthStore((s) => s.igrejaMaeId)
  const role = useAuthStore((s) => s.role)
  const ehAdmin = podeGerenciarAssinatura(role)
  const ehIgrejaFilha = !!igrejaMaeId
  const podeFazerUpgrade = ehAdmin && !ehIgrejaFilha
  const { saindo, fechar } = useFecharAnimado(onFechar, 200)

  useEffect(() => {
    if (!aberto) return
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aberto, fechar])

  if (!aberto) return null

  const nomeSingular = congregacao.singular.toLowerCase()
  const genero = congregacao.genero

  let prefixoFamilia = 'Sua rede de igrejas'
  if (nomeSingular.includes('congreg') || nomeSingular.includes('congregação') || nomeSingular.includes('congregacao')) {
    prefixoFamilia = 'Suas congregações'
  } else if (genero === 'FEMININO') {
    prefixoFamilia = `Sua ${nomeSingular} de igrejas`
  } else if (genero === 'MASCULINO') {
    prefixoFamilia = `Seu ${nomeSingular} de igrejas`
  }

  return (
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      onMouseDown={fechar}
      role="dialog"
      aria-modal="true"
      aria-labelledby="titulo-limite-excedido"
    >
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <span className={styles.grabber} aria-hidden="true" />

        <div className={styles.modalHeader}>
          <div className={styles.iconeBox}>
            <AlertTriangle size={24} aria-hidden="true" />
          </div>
          <button
            type="button"
            className={styles.btnFecharX}
            onClick={fechar}
            aria-label="Fechar"
          >
            <X size={18} />
          </button>
        </div>

        <div className={styles.corpo}>
          <h2 className={styles.titulo} id="titulo-limite-excedido">
            Limite do Plano Atingido
          </h2>
          <p className={styles.descricao}>
            {prefixoFamilia} atingiram a capacidade máxima de {limite ? `${limite} ` : ''}pessoas ativas permitidas pelo plano contratado.
          </p>

          {ehIgrejaFilha ? (
            <div className={styles.caixaAlerta}>
              <p className={styles.caixaAlertaTitulo}>
                <Lock size={14} aria-hidden="true" style={{ display: 'inline', marginRight: '6px' }} />
                Upgrade restrito à Igreja Contratante
              </p>
              Esta congregação está vinculada a um plano gerenciado pela igreja matriz. Entre em contato com a administração da igreja contratante para solicitar o aumento da capacidade.
            </div>
          ) : !ehAdmin ? (
            <div className={styles.caixaAlerta}>
              <p className={styles.caixaAlertaTitulo}>
                <Lock size={14} aria-hidden="true" style={{ display: 'inline', marginRight: '6px' }} />
                Upgrade restrito ao Administrador
              </p>
              Sua igreja atingiu o limite de pessoas ativas do plano atual. Entre em contato com o administrador da sua comunidade para solicitar o aumento da capacidade.
            </div>
          ) : (
            <div className={styles.caixaInfo}>
              Para continuar cadastrando novas pessoas e liberar recursos avançados, faça o upgrade do seu plano Domus.
            </div>
          )}
        </div>

        <div className={styles.rodape}>
          <button type="button" onClick={fechar} className={styles.btnSecundario}>
            {!podeFazerUpgrade ? 'Entendi' : 'Fechar'}
          </button>

          {podeFazerUpgrade && (
            <Link
              href="/planos"
              className={styles.btnPrimario}
              onClick={fechar}
            >
              <span>Fazer Upgrade do Plano</span>
              <ArrowRight size={16} />
            </Link>
          )}
        </div>
      </div>
    </div>
  )
}
