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
    <div className="min-h-screen bg-slate-950 text-slate-100 p-8 font-sans space-y-8">
      {simularPausado && <BannerAssinaturaPausada />}

      <div className="max-w-2xl mx-auto bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6">
        <div>
          <h1 className="text-xl font-bold text-indigo-400">⚡ Painel Dev de Prévia de UX / Modais</h1>
          <p className="text-xs text-slate-400 mt-1">
            Ferramenta interna de desenvolvimento para visualizar e validar os componentes de transbordo e bloqueios sem precisar cadastrar dados no banco.
          </p>
        </div>

        <div className="space-y-4">
          <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-3">
            <h2 className="text-sm font-semibold text-white">1. Modal de Limite de Cota Excedido (Igreja Mãe vs Filha)</h2>
            <div className="flex flex-wrap gap-3">
              <button
                onClick={() => {
                  useAuthStore.setState({ igrejaMaeId: null }) // Simula Igreja Mãe
                  setModalLimiteMaeAberto(true)
                }}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold transition"
              >
                Abrir Modal (Igreja Mãe - Com Botão Upgrade)
              </button>

              <button
                onClick={() => {
                  useAuthStore.setState({ igrejaMaeId: 'mae-123' }) // Simula Igreja Filha
                  setModalLimiteFilhaAberto(true)
                }}
                className="px-4 py-2 bg-amber-600 hover:bg-amber-500 text-white rounded-lg text-xs font-semibold transition"
              >
                Abrir Modal (Igreja Filha - Restrito)
              </button>
            </div>
          </div>

          <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-3">
            <h2 className="text-sm font-semibold text-white">2. Banner de Dunning (Status PAUSADA - Leitura Apenas)</h2>
            <button
              onClick={toggleSimulacaoPausada}
              className={`px-4 py-2 rounded-lg text-xs font-semibold transition ${simularPausado ? 'bg-red-600 hover:bg-red-500 text-white' : 'bg-emerald-600 hover:bg-emerald-500 text-white'}`}
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
