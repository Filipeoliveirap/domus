'use client'

import React, { useEffect, useState } from 'react'
import { useAdminAuth } from '@/contexts/AdminAuthContext'
import { adminTenantService, ConfiguracaoMercadoPagoDTO } from '@/services/adminTenantService'

export default function AdminConfiguracoesPage() {
  const { adminToken } = useAdminAuth()
  const [config, setConfig] = useState<ConfiguracaoMercadoPagoDTO | null>(null)
  const [accessToken, setAccessToken] = useState('')
  const [publicKey, setPublicKey] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [salvando, setSalvando] = useState(false)
  const [mensagem, setMensagem] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    if (!adminToken) return

    adminTenantService
      .obterConfiguracaoMercadoPago(adminToken)
      .then((res) => {
        setConfig(res)
        setAccessToken(res.accessToken || '')
        setPublicKey(res.publicKey || '')
      })
      .catch((err) => setErro(err?.message || 'Erro ao carregar configurações'))
      .finally(() => setCarregando(false))
  }, [adminToken])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!adminToken) return
    setSalvando(true)
    setMensagem(null)
    setErro(null)

    try {
      const res = await adminTenantService.salvarConfiguracaoMercadoPago(adminToken, {
        accessToken,
        publicKey,
      })
      setConfig(res)
      setAccessToken(res.accessToken || '')
      setPublicKey(res.publicKey || '')
      setMensagem('Credenciais do Mercado Pago salvas e criptografadas com sucesso!')
    } catch (err: any) {
      setErro(err?.response?.data?.message || 'Erro ao salvar credenciais do Mercado Pago.')
    } finally {
      setSalvando(false)
    }
  }

  if (carregando) {
    return <div className="p-8 text-center text-slate-400">Carregando configurações...</div>
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white">Configuração de Recebimento (Mercado Pago Master)</h1>
        <p className="text-slate-400 text-sm mt-1">
          Credencial da plataforma Domus para receber assinaturas recorrentes SaaS dos clientes.
        </p>
      </div>

      {mensagem && (
        <div className="p-4 bg-emerald-950/40 border border-emerald-800 text-emerald-300 rounded-lg text-sm">
          {mensagem}
        </div>
      )}

      {erro && (
        <div className="p-4 bg-red-950/40 border border-red-800 text-red-300 rounded-lg text-sm">
          {erro}
        </div>
      )}

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-lg space-y-6">
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-base font-semibold text-white">Status da Conta Master Domus</h2>
            <p className="text-xs text-slate-500">Usada para cobrança de mensalidades (`MercadoPagoSubscriptionService`)</p>
          </div>
          {config?.configurado ? (
            <span className="bg-emerald-950 text-emerald-300 border border-emerald-800 px-3 py-1 rounded-full text-xs font-semibold">
              ● Configurado
            </span>
          ) : (
            <span className="bg-amber-950 text-amber-300 border border-amber-800 px-3 py-1 rounded-full text-xs font-semibold">
              ⚠️ Não Configurado
            </span>
          )}
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase mb-2">
              Access Token do Mercado Pago (Master)
            </label>
            <input
              type="text"
              value={accessToken}
              onChange={(e) => setAccessToken(e.target.value)}
              placeholder="APP_USR-..."
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-2.5 text-white font-mono text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <p className="text-xs text-slate-500 mt-1">
              Obtido no painel de desenvolvedores do Mercado Pago (Credenciais de Produção). Criptografado no banco.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase mb-2">
              Public Key (Master)
            </label>
            <input
              type="text"
              value={publicKey}
              onChange={(e) => setPublicKey(e.target.value)}
              placeholder="APP_USR-..."
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-2.5 text-white font-mono text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <button
            type="submit"
            disabled={salvando}
            className="bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-950 text-white font-semibold px-6 py-2.5 rounded-lg text-sm transition shadow-lg shadow-indigo-600/20"
          >
            {salvando ? 'Salvando...' : 'Salvar Credenciais Master'}
          </button>
        </form>
      </div>
    </div>
  )
}
