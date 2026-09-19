'use client'

import { useCallback } from 'react'
import { useUiStore } from '@/store/uiStore'

const DURACAO_MS = 1200

/**
 * Mostra o mesmo selo "Inscrição feita!" do checkout (`<PonteParaCheckout>`, montado no
 * layout raiz), sem legenda, por um instante — pra qualquer confirmação de "Se inscrever"
 * que NÃO navega pra lugar nenhum (evento grátis, ou pago só depois de contornar
 * elegibilidade sem cobrança pendente). O fluxo que navega pro checkout usa
 * `useIrParaCheckout`, que já mostra o mesmo véu com a legenda "Abrindo o pagamento…".
 */
export function useMostrarInscricaoFeita() {
  const abrirPonteCheckout = useUiStore((s) => s.abrirPonteCheckout)
  const fecharPonteCheckout = useUiStore((s) => s.fecharPonteCheckout)

  return useCallback(() => {
    abrirPonteCheckout(null)
    window.setTimeout(fecharPonteCheckout, DURACAO_MS)
  }, [abrirPonteCheckout, fecharPonteCheckout])
}
