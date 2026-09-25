'use client'

import React, { useState } from 'react'
import { ModalLimiteExcedido } from '@/components/module/pessoas/ModalLimiteExcedido'
import { BannerAssinaturaPausada } from '@/components/common/BannerAssinaturaPausada/BannerAssinaturaPausada'
import { useAuthStore } from '@/store/authStore'

export default function PreviewModaisDevPage() {
  const [modalLimiteMaeAberto, setModalLimiteMaeAberto] = useState(false)
  const [modalLimiteFilhaAberto, setModalLimiteFilhaAberto] = useState(false)
  const [simularPausado, setSimularPausado] = useState(false)

  const loginStore = useAuthStore((s) => s.login)
  const logoutStore = useAuthStore((s) => s.logout)

  const toggleSimulacaoPausada = () => {
    if (simularPausado) {
      logoutStore()
      setSimularPausado(false)
    } else {
      loginStore({
        id: 'user-1',
        nome: 'Admin Teste',
        role: { id: 'r1', nome: 'ADMIN_IGREJA' },
        igrejaId: 'ig-1',
        igrejaNome: 'Igreja Teste',
        fotoId: null,
        cargo: 'Pastor',
        igrejaSigla: 'IT',
        igrejaLogoId: null,
        precisaAceitarTermos: false,
        termosAceitosEm: null,
        rotulos: null,
        igrejaMaeId: null,
        statusAssinatura: 'PAUSADA',
      })
      setSimularPausado(true)
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-page)', color: 'var(--color-text-primary)', padding: '32px', fontFamily: 'sans-serif' }}>
      {simularPausado && <BannerAssinaturaPausada />}

      <div style={{ maxWidth: '640px', margin: '0 auto', background: 'var(--color-bg-white)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: '24px', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}>
        <div style={{ marginBottom: '24px' }}>
          <h1 style={{ fontSize: '20px', fontWeight: 'bold', color: 'var(--color-primary)' }}>⚡ Painel Dev de Prévia de UX / Modais</h1>
          <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', marginTop: '4px' }}>
            Ferramenta interna de desenvolvimento para visualizar e validar os componentes de transbordo e bloqueios sem precisar cadastrar dados no banco.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div style={{ padding: '16px', background: 'var(--color-bg-page)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <h2 style={{ fontSize: '14px', fontWeight: '600', color: 'var(--color-text-primary)', marginBottom: '12px' }}>1. Modal de Limite de Cota Excedido (Igreja Mãe vs Filha)</h2>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px' }}>
              <button
                onClick={() => {
                  useAuthStore.setState({ igrejaMaeId: null }) // Simula Igreja Mãe
                  setModalLimiteMaeAberto(true)
                }}
                style={{ padding: '8px 16px', background: 'var(--color-primary)', color: '#fff', border: 'none', borderRadius: 'var(--radius-md)', fontSize: '13px', fontWeight: '600', cursor: 'pointer' }}
              >
                Abrir Modal (Igreja Mãe - Com Botão Upgrade)
              </button>

              <button
                onClick={() => {
                  useAuthStore.setState({ igrejaMaeId: 'mae-123' }) // Simula Igreja Filha
                  setModalLimiteFilhaAberto(true)
                }}
                style={{ padding: '8px 16px', background: '#D97706', color: '#fff', border: 'none', borderRadius: 'var(--radius-md)', fontSize: '13px', fontWeight: '600', cursor: 'pointer' }}
              >
                Abrir Modal (Igreja Filha - Restrito)
              </button>
            </div>
          </div>

          <div style={{ padding: '16px', background: 'var(--color-bg-page)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <h2 style={{ fontSize: '14px', fontWeight: '600', color: 'var(--color-text-primary)', marginBottom: '12px' }}>2. Banner de Dunning (Status PAUSADA - Leitura Apenas)</h2>
            <button
              onClick={toggleSimulacaoPausada}
              style={{ padding: '8px 16px', background: simularPausado ? '#DC2626' : '#059669', color: '#fff', border: 'none', borderRadius: 'var(--radius-md)', fontSize: '13px', fontWeight: '600', cursor: 'pointer' }}
            >
              {simularPausado ? 'Desativar Banner Pausado' : 'Simular Status PAUSADA (Modo Leitura)'}
            </button>
          </div>
        </div>
      </div>

      {/* Modal para Igreja Mãe */}
      <ModalLimiteExcedido
        aberto={modalLimiteMaeAberto}
        onFechar={() => setModalLimiteMaeAberto(false)}
        limite={60}
      />

      {/* Modal para Igreja Filha */}
      <ModalLimiteExcedido
        aberto={modalLimiteFilhaAberto}
        onFechar={() => setModalLimiteFilhaAberto(false)}
        limite={60}
      />
    </div>
  )
}
