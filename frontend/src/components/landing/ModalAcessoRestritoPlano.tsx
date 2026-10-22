'use client'

import React from 'react'
import { Lock, X } from 'lucide-react'
import styles from '@/components/module/assinatura/ModalAlterarPlano.module.css'

interface Props {
  aoFechar: () => void
}

export function ModalAcessoRestritoPlano({ aoFechar }: Props) {
  return (
    <div className={styles.overlay} onClick={aoFechar} role="dialog" aria-modal="true">
      <div className={styles.modal} onClick={(e) => e.stopPropagation()} style={{ maxWidth: '440px' }}>
        <div className={styles.cabecalho}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '9999px',
              background: '#fef3c7',
              color: '#d97706',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <Lock size={18} aria-hidden="true" />
            </div>
            <h2 className={styles.titulo} style={{ fontSize: '18px', margin: 0 }}>Acesso Restrito</h2>
          </div>
          <button type="button" onClick={aoFechar} className={styles.btnFechar} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div style={{ padding: '8px 0 16px 0' }}>
          <p style={{ fontSize: '14px', color: 'var(--color-text-secondary, #475569)', lineHeight: 1.5, margin: 0 }}>
            Você não possui permissão para alterar ou contratar planos. Apenas administradores da igreja podem gerenciar assinaturas.
          </p>
          <div style={{
            marginTop: '16px',
            padding: '12px 14px',
            background: 'var(--color-bg-page, #f8fafc)',
            border: '1px solid var(--color-border, #e2e8f0)',
            borderRadius: '8px',
            fontSize: '13px',
            color: 'var(--color-text-secondary, #334155)',
            lineHeight: 1.4
          }}>
            Entre em contato com o administrador da sua comunidade para solicitar o upgrade de plano.
          </div>
        </div>

        <div className={styles.acoes} style={{ marginTop: '12px' }}>
          <button type="button" onClick={aoFechar} className={styles.btnConfirmar} style={{ width: '100%', justifyContent: 'center' }}>
            Entendi
          </button>
        </div>
      </div>
    </div>
  )
}
