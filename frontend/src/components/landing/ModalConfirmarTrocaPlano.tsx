'use client'

import React, { useState } from 'react'
import { X, Sparkles, Check, ArrowRight } from 'lucide-react'
import { PlanoServidor } from '@/services/plano.service'
import { ResumoAssinatura, alterarPlanoAssinatura } from '@/services/assinatura.service'
import styles from '@/components/module/assinatura/ModalAlterarPlano.module.css'

interface Props {
  plano: PlanoServidor
  resumo: ResumoAssinatura
  aoFechar: () => void
  aoSucesso: (plano: PlanoServidor) => void
}

export function ModalConfirmarTrocaPlano({ plano, resumo, aoFechar, aoSucesso }: Props) {
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const handleConfirmar = async () => {
    setSalvando(true)
    setErro(null)
    try {
      await alterarPlanoAssinatura(plano.id)
      aoSucesso(plano)
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Não foi possível alterar o plano no momento.'
      setErro(msg)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className={styles.overlay} onClick={aoFechar} role="dialog" aria-modal="true">
      <div className={styles.modal} onClick={(e) => e.stopPropagation()} style={{ maxWidth: '440px' }}>
        <div className={styles.cabecalho}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Sparkles size={20} color="var(--color-primary, #2563eb)" />
            <h2 className={styles.titulo} style={{ margin: 0 }}>Confirmar Novo Plano</h2>
          </div>
          <button type="button" onClick={aoFechar} className={styles.btnFechar} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div style={{ padding: '12px 0 16px 0' }}>
          <p style={{ fontSize: '14px', color: 'var(--color-text-secondary, #475569)', lineHeight: 1.5, margin: 0 }}>
            Você está prestes a migrar o plano da sua igreja para o <strong>{plano.nomeExibicao}</strong>.
          </p>

          <div style={{
            marginTop: '16px',
            background: 'var(--color-bg-page, #f8fafc)',
            border: '1px solid var(--color-border, #e2e8f0)',
            borderRadius: '10px',
            padding: '14px 16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '10px'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span style={{ color: 'var(--color-text-muted, #64748b)' }}>Plano Atual:</span>
              <span style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>{resumo.nomePlano}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span style={{ color: 'var(--color-text-muted, #64748b)' }}>Novo Plano:</span>
              <span style={{ fontWeight: 700, color: 'var(--color-primary, #2563eb)' }}>{plano.nomeExibicao}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span style={{ color: 'var(--color-text-muted, #64748b)' }}>Nova Mensalidade:</span>
              <span style={{ fontWeight: 700, color: 'var(--color-text-primary)' }}>
                R$ {plano.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })} / mês
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span style={{ color: 'var(--color-text-muted, #64748b)' }}>Forma de Pagamento:</span>
              <span style={{ fontWeight: 600, color: 'var(--color-text-primary)' }}>Cartão já cadastrado</span>
            </div>
          </div>

          <p style={{ marginTop: '14px', fontSize: '12px', color: 'var(--color-text-muted, #64748b)', lineHeight: 1.4 }}>
            A nova capacidade e os novos recursos são liberados imediatamente. A cobrança do novo valor ocorrerá no próximo vencimento do seu ciclo.
          </p>

          {erro && <div className={styles.erro} style={{ marginTop: '12px' }}>{erro}</div>}
        </div>

        <div className={styles.acoes} style={{ marginTop: '12px' }}>
          <button type="button" onClick={aoFechar} className={styles.btnCancelar} disabled={salvando}>
            Cancelar
          </button>
          <button
            type="button"
            onClick={handleConfirmar}
            className={styles.btnConfirmar}
            disabled={salvando}
          >
            {salvando ? 'Atualizando...' : 'Confirmar Mudança'}
          </button>
        </div>
      </div>
    </div>
  )
}
