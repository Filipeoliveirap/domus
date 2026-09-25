'use client'

import React, { useState } from 'react'
import { useAdminAuth } from '@/contexts/AdminAuthContext'

export default function AdminLoginPage() {
  const { login } = useAdminAuth()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro(null)
    setCarregando(true)

    try {
      await login({ email, senha })
    } catch (err: any) {
      setErro(err?.response?.data?.message || 'Credenciais inválidas de administrador.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-xl p-8 shadow-2xl">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-extrabold text-indigo-400">Domus Admin</h1>
          <p className="text-slate-400 text-sm mt-2">Painel de Gestão e Monitoramento de Tenants</p>
        </div>

        {erro && (
          <div className="mb-6 p-3 bg-red-900/30 border border-red-700/50 rounded-lg text-red-300 text-sm">
            {erro}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase mb-2">E-mail de Administrador</label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@domus.com"
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase mb-2">Senha</label>
            <input
              type="password"
              required
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              placeholder="••••••••"
              className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <button
            type="submit"
            disabled={carregando}
            className="w-full bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-950 disabled:text-slate-600 text-white font-semibold py-3 rounded-lg transition duration-200 shadow-lg shadow-indigo-600/20"
          >
            {carregando ? 'Entrando...' : 'Acessar Painel Admin'}
          </button>
        </form>
      </div>
    </div>
  )
}
