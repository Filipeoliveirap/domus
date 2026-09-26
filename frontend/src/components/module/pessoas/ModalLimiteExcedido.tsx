'use client'

import React from 'react'
import Link from 'next/link'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { useAuthStore } from '@/store/authStore'
import styles from './ModalLimiteExcedido.module.css'

interface ModalLimiteExcedidoProps {
  aberto: boolean
  onFechar: () => void
  limite?: number
}

export function ModalLimiteExcedido({ aberto, onFechar, limite }: ModalLimiteExcedidoProps) {
  const { congregacao } = useRotulos()
  const igrejaMaeId = useAuthStore((s) => s.igrejaMaeId)
  const ehIgrejaFilha = !!igrejaMaeId

  if (!aberto) return null

  const nomeSingular = congregacao.singular.toLowerCase()
  const genero = congregacao.genero

  // Formatação dinâmica do texto com base na nomenclatura da igreja (rotulos)
  let prefixoFamilia = 'Sua rede de igrejas'
  if (nomeSingular.includes('congreg') || nomeSingular.includes('congregação') || nomeSingular.includes('congregacao')) {
    prefixoFamilia = 'Suas congregações'
  } else if (genero === 'FEMININO') {
    prefixoFamilia = `Sua ${nomeSingular} de igrejas`
  } else if (genero === 'MASCULINO') {
    prefixoFamilia = `Seu ${nomeSingular} de igrejas`
  }

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.iconeBox}>
          ⚠️
        </div>

        <div>
          <h2 className={styles.titulo}>Limite do Plano Atingido</h2>
          <p className={styles.descricao}>
            {prefixoFamilia} atingiram a capacidade máxima de {limite ? `${limite} ` : ''}pessoas ativas permitidas pelo plano contratado.
          </p>
        </div>

        {ehIgrejaFilha ? (
          <div className={styles.caixaAlerta}>
            <p className={styles.caixaAlertaTitulo}>🔒 Upgrade restrito à Igreja Contratante</p>
            Esta congregação está vinculada a um plano gerenciado pela igreja matriz. Entre em contato com a administração da igreja contratante para solicitar o aumento da capacidade.
          </div>
        ) : (
          <div className={styles.caixaInfo}>
            Para continuar cadastrando novas pessoas e liberar recursos avançados, faça o upgrade do seu plano Domus.
          </div>
        )}

        <div className={styles.botoesGroup}>
          {!ehIgrejaFilha && (
            <Link href="/configuracoes/assinatura" className={styles.btnPrimary}>
              Fazer Upgrade do Plano
            </Link>
          )}

          <button onClick={onFechar} className={styles.btnSecondary}>
            {ehIgrejaFilha ? 'Entendi' : 'Fechar'}
          </button>
        </div>
      </div>
    </div>
  )
}
