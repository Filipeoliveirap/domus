'use client'

import React, { useEffect, useState } from 'react'
import Link from 'next/link'
import { useAdminAuth } from '@/contexts/AdminAuthContext'
import { adminTenantService, AdminDashboardDTO } from '@/services/adminTenantService'

export default function AdminDashboardPage() {
  const { adminToken } = useAdminAuth()
  const [data, setData] = useState<AdminDashboardDTO | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    if (!adminToken) return

    adminTenantService
      .obterDashboardMetrics(adminToken)
      .then(setData)
      .catch((err) => setErro(err?.message || 'Erro ao carregar métricas'))
      .finally(() => setCarregando(false))
  }, [adminToken])

  if (carregando) {
    return <div className="p-8 text-center text-slate-400">Carregando métricas...</div>
  }

  if (erro || !data) {
    return <div className="p-8 text-center text-red-400 bg-red-950/20 border border-red-900 rounded-lg">{erro || 'Erro ao carregar dados'}</div>
  }

  const formatarMoeda = (val: number) =>
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(val)

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white">Dashboard Operacional SaaS</h1>
        <p className="text-slate-400 text-sm mt-1">Visão geral da plataforma Domus e consumo de recursos pelos clientes.</p>
      </div>

      {/* Grid de KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">MRR Estimado</span>
          <div className="text-3xl font-extrabold text-emerald-400 mt-2">{formatarMoeda(data.mrrEstimado)}</div>
          <span className="text-xs text-slate-500 mt-1 block">Assinaturas ativas recorrentes</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Total de Tenants</span>
          <div className="text-3xl font-extrabold text-indigo-400 mt-2">{data.totalIgrejas}</div>
          <div className="flex gap-3 text-xs mt-2 text-slate-400">
            <span className="text-emerald-400 font-semibold">{data.igrejasAtivas} ativas</span>
            <span>•</span>
            <span className="text-red-400 font-semibold">{data.igrejasSuspensas} suspensas</span>
            <span>•</span>
            <span className="text-amber-400 font-semibold">{data.igrejasTrial} trial</span>
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Pessoas Cadastradas</span>
          <div className="text-3xl font-extrabold text-blue-400 mt-2">{data.totalPessoasAtivas}</div>
          <span className="text-xs text-slate-500 mt-1 block">Volume total de dados gerenciados</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Alertas de Capacidade</span>
          <div className={`text-3xl font-extrabold mt-2 ${data.alertasLimite.length > 0 ? 'text-amber-400' : 'text-slate-400'}`}>
            {data.alertasLimite.length}
          </div>
          <span className="text-xs text-slate-500 mt-1 block">Tenants a &gt;=90% do limite do plano</span>
        </div>
      </div>

      {/* Alertas de Capacidade */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-lg">
        <h2 className="text-lg font-bold text-white mb-4 flex items-center gap-2">
          <span>⚠️ Igrejas Próximas do Limite de Pessoas</span>
        </h2>

        {data.alertasLimite.length === 0 ? (
          <p className="text-sm text-slate-500 py-4 text-center">Nenhum tenant atingiu a margem de 90% da capacidade do plano.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-950 text-slate-400 text-xs uppercase border-b border-slate-800">
                <tr>
                  <th className="p-3">Tenant</th>
                  <th className="p-3">Plano</th>
                  <th className="p-3">Pessoas Ativas / Limite</th>
                  <th className="p-3">% Uso</th>
                  <th className="p-3 text-right">Ação</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {data.alertasLimite.map((alerta) => (
                  <tr key={alerta.igrejaId} className="hover:bg-slate-850">
                    <td className="p-3 font-semibold text-white">{alerta.nome}</td>
                    <td className="p-3">
                      <span className="bg-indigo-950 text-indigo-300 border border-indigo-800 px-2 py-0.5 rounded text-xs">
                        {alerta.plano}
                      </span>
                    </td>
                    <td className="p-3">{alerta.pessoasAtivas} / {alerta.limitePessoas}</td>
                    <td className="p-3 font-bold text-amber-400">{alerta.percentualUso}%</td>
                    <td className="p-3 text-right">
                      <Link
                        href={`/admin/tenants/${alerta.igrejaId}`}
                        className="text-xs bg-indigo-600 hover:bg-indigo-500 text-white px-3 py-1.5 rounded transition"
                      >
                        Gerenciar Tenant
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
