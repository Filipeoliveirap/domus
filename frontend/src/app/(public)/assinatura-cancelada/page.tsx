'use client'

import React from 'react'
import Link from 'next/link'

export default function AssinaturaCanceladaPage() {
  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-4 font-sans text-slate-100">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl text-center space-y-6">
        <div className="w-16 h-16 bg-red-500/10 border border-red-500/20 text-red-400 rounded-full flex items-center justify-center mx-auto text-3xl font-bold">
          ⚠️
        </div>

        <div className="space-y-2">
          <h1 className="text-2xl font-bold text-white">Assinatura Cancelada</h1>
          <p className="text-slate-400 text-sm leading-relaxed">
            A assinatura do plano Domus da sua igreja foi cancelada. O acesso aos módulos e recursos foi temporariamente interrompido.
          </p>
        </div>

        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-left text-xs text-slate-400 leading-relaxed">
          <p className="font-semibold text-slate-300 mb-1">Como reativar sua conta?</p>
          Como administrador, você pode reativar seu plano imediatamente escolhendo uma opção de pagamento abaixo. Todos os dados da sua igreja foram preservados.
        </div>

        <div className="flex flex-col gap-3 pt-2">
          <Link
            href="/planos"
            className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-semibold py-3 rounded-xl text-sm transition shadow-lg shadow-indigo-600/20 text-center"
          >
            Reativar Assinatura Agora
          </Link>

          <Link
            href="/login"
            className="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold py-2.5 rounded-xl text-sm transition text-center"
          >
            Voltar para o Login
          </Link>
        </div>
      </div>
    </div>
  )
}
