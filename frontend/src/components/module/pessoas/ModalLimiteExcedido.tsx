'use client'

import React from 'react'
import Link from 'next/link'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { useAuthStore } from '@/store/authStore'

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
    <div className="fixed inset-0 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 z-50 animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 text-center shadow-2xl space-y-5">
        <div className="w-16 h-16 bg-amber-500/10 border border-amber-500/20 text-amber-400 rounded-full flex items-center justify-center mx-auto text-2xl font-bold">
          ⚠️
        </div>

        <div>
          <h2 className="text-xl font-bold text-white">Limite do Plano Atingido</h2>
          <p className="text-sm text-slate-300 mt-2 leading-relaxed">
            {prefixoFamilia} atingiram a capacidade máxima de {limite ? `${limite} ` : ''}pessoas ativas permitidas pelo plano contratado.
          </p>
        </div>

        {ehIgrejaFilha ? (
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-left text-xs text-amber-300/90 leading-relaxed">
            <p className="font-semibold text-amber-300 mb-1">🔒 Upgrade restrito à Igreja Contratante</p>
            Esta congregação está vinculada a um plano gerenciado pela igreja matriz. Entre em contato com a administração da igreja contratante para solicitar o aumento da capacidade.
          </div>
        ) : (
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-left text-xs text-slate-400 leading-relaxed">
            Para continuar cadastrando novas pessoas e liberar recursos avançados, faça o upgrade do seu plano Domus.
          </div>
        )}

        <div className="flex flex-col gap-2 pt-2">
          {!ehIgrejaFilha && (
            <Link
              href="/configuracoes/assinatura"
              className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-semibold py-2.5 rounded-xl text-sm transition text-center shadow-lg shadow-indigo-600/20"
            >
              Fazer Upgrade do Plano
            </Link>
          )}

          <button
            onClick={onFechar}
            className="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold py-2.5 rounded-xl text-sm transition"
          >
            {ehIgrejaFilha ? 'Entendi' : 'Fechar'}
          </button>
        </div>
      </div>
    </div>
  )
}
