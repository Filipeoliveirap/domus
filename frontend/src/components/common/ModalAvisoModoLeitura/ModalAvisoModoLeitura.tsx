'use client'

import React, { useState } from 'react'
import Link from 'next/link'
import { AlertTriangle, X, ArrowRight, CreditCard, Lock } from 'lucide-react'
import { useModoLeituraStore } from '@/store/modoLeituraStore'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarAssinatura } from '@/lib/permissoes'
import { ModalAtualizarCartao } from '@/components/module/assinatura/ModalAtualizarCartao'
import { notificar } from '@/components/common/Notificacao/notificar'
import styles from './ModalAvisoModoLeitura.module.css'

export function ModalAvisoModoLeitura() {
  const { aberto, motivo, mensagem, fechar } = useModoLeituraStore()
  const role = useAuthStore((s) => s.role)
  const ehAdmin = podeGerenciarAssinatura(role)
  const [modalCartaoAberto, setModalCartaoAberto] = useState(false)

  if (!aberto) return null

  const ehDesvinculada = motivo === 'DESVINCULADA'

  const handleSucessoCartao = () => {
    setModalCartaoAberto(false)
    fechar()
    notificar.sucesso('Cartão atualizado com sucesso!', 'Sua assinatura foi reprocessada.')
  }

  return (
    <>
      <div className={styles.overlay} onClick={fechar} role="dialog" aria-modal="true">
        <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
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
            <h2 className={styles.titulo}>
              {ehDesvinculada ? 'Modo Somente Leitura' : 'Assinatura Pausada'}
            </h2>

            <p className={styles.descricao}>
              {ehAdmin ? (
                ehDesvinculada
                  ? (mensagem || 'Sua igreja está em modo somente leitura após ser desvinculada da sede. Contrate um plano próprio para liberar alterações no sistema.')
                  : (mensagem || 'Não foi possível renovar sua assinatura neste mês por pendência no pagamento do cartão. Atualize os dados do cartão para liberar edições no sistema.')
              ) : (
                'O sistema está temporariamente em modo somente leitura. Apenas administradores podem gerenciar e atualizar a assinatura da igreja. Por favor, entre em contato com o administrador da sua comunidade para normalizar as permissões.'
              )}
            </p>

            <div className={styles.caixaDestaque}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, color: 'var(--color-text-primary)' }}>
                <Lock size={15} color="var(--color-warning, #d97706)" />
                <span>Operações de escrita bloqueadas</span>
              </div>
              <p style={{ margin: '6px 0 0 0', fontSize: '12px', color: 'var(--color-text-secondary)' }}>
                Novos cadastros de pessoas, eventos, movimentações financeiras e células estão pausados temporariamente.
              </p>
            </div>
          </div>

          <div className={styles.rodape}>
            <button type="button" onClick={fechar} className={styles.btnSecundario}>
              {ehAdmin ? 'Cancelar' : 'Entendi'}
            </button>

            {ehAdmin && (
              ehDesvinculada ? (
                <Link
                  href="/planos"
                  className={styles.btnPrimario}
                  onClick={fechar}
                >
                  <span>Escolher Plano</span>
                  <ArrowRight size={16} />
                </Link>
              ) : (
                <button
                  type="button"
                  className={styles.btnPrimario}
                  onClick={() => setModalCartaoAberto(true)}
                >
                  <CreditCard size={16} />
                  <span>Atualizar Cartão</span>
                </button>
              )
            )}
          </div>
        </div>
      </div>

      {modalCartaoAberto && (
        <ModalAtualizarCartao
          aoFechar={() => setModalCartaoAberto(false)}
          aoSucesso={handleSucessoCartao}
        />
      )}
    </>
  )
}
