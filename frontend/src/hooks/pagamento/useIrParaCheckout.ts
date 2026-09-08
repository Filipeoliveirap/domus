'use client'

import { useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useUiStore } from '@/store/uiStore'

const ATRASO_PONTE_MS = 550
const SEGURANCA_MS = 5000

/**
 * Leva pro checkout de evento pago com a "ponte": mostra o véu "Inscrição feita! / Abrindo
 * o pagamento…" (`<PonteParaCheckout>`, montado no layout raiz), deixa o drawer/modal que
 * disparou a ação animar a saída atrás do vidro fosco (~0,5s) e só então faz o `router.push`
 * pra rota full-screen do checkout — que fecha a ponte sozinha ao montar.
 *
 * Mesmo comportamento em todos os caminhos que criam uma cobrança: auto-inscrição,
 * gestor inscrevendo alguém, e convite público (dentro ou fora do app shell).
 */
export function useIrParaCheckout() {
  const router = useRouter()
  const abrirPonteCheckout = useUiStore((s) => s.abrirPonteCheckout)
  const fecharPonteCheckout = useUiStore((s) => s.fecharPonteCheckout)

  return useCallback(
    (
      eventoId: string,
      cobrancaId: string,
      aoAntesDeNavegar?: () => void,
      /** Espera antes do push — tempo pra um drawer/modal animar a saída atrás do véu.
       *  Fluxos sem modal (convite público) passam 0: o véu aparece e navega na hora,
       *  sem stall (o `loading.tsx` da rota assume em seguida). */
      atrasoMs = ATRASO_PONTE_MS,
    ) => {
      const destino = `/eventos/${eventoId}/pagamento/${cobrancaId}`
      router.prefetch(destino)
      abrirPonteCheckout()
      aoAntesDeNavegar?.()
      if (atrasoMs > 0) window.setTimeout(() => router.push(destino), atrasoMs)
      else router.push(destino)
      // Se a navegação falhar, não deixa o véu preso.
      window.setTimeout(fecharPonteCheckout, SEGURANCA_MS)
    },
    [router, abrirPonteCheckout, fecharPonteCheckout],
  )
}
