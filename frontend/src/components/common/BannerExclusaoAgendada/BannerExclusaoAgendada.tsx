'use client'

import { useEffect, useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { notificar } from '@/components/common/Notificacao/notificar'
import styles from './BannerExclusaoAgendada.module.css'

/**
 * Fixo no topo, visível em toda tela autenticada, só para ADMIN_IGREJA.
 *
 * `GET /auth/me` (sessão) não carrega exclusaoAgendadaEm/diasRestantes — só
 * `/igrejas/minha` traz esses campos (IgrejaDetalheDTO). Como o banner precisa
 * aparecer em qualquer tela, e não só em Configurações (onde `useMinhaIgreja` já
 * era chamado), ele mesmo dispara a busca — o React Query dedupe pela mesma
 * `queryKey` evita uma segunda requisição quando a tela de Configurações também
 * está montada.
 */
export function BannerExclusaoAgendada() {
  const role = useAuthStore((s) => s.role)
  const exclusaoAgendadaEm = useAuthStore((s) => s.exclusaoAgendadaEm)
  const diasRestantes = useAuthStore((s) => s.diasRestantes)
  const atualizarExclusaoAgendada = useAuthStore((s) => s.atualizarExclusaoAgendada)
  const [cancelando, setCancelando] = useState(false)

  const ehAdmin = role === 'ADMIN_IGREJA'
  const { data: igreja } = useMinhaIgreja(ehAdmin)

  useEffect(() => {
    if (!igreja) return
    atualizarExclusaoAgendada(igreja.exclusaoAgendadaEm, igreja.diasRestantes)
  }, [igreja, atualizarExclusaoAgendada])

  if (!ehAdmin || !exclusaoAgendadaEm) return null

  async function cancelar() {
    setCancelando(true)
    try {
      await api.post(Endpoints.igreja.exclusao.CANCELAR)
      atualizarExclusaoAgendada(null, null)
      notificar.sucesso('Exclusão cancelada', 'A igreja não será mais excluída.')
    } catch (erro: unknown) {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível cancelar a exclusão', mensagem)
    } finally {
      setCancelando(false)
    }
  }

  return (
    <div className={styles.banner} role="alert">
      <span className={styles.texto}>
        <AlertTriangle size={16} aria-hidden="true" />
        Esta igreja será excluída definitivamente em {diasRestantes} dia(s).
      </span>
      <button
        type="button"
        className={styles.botao}
        onClick={cancelar}
        disabled={cancelando}
      >
        {cancelando ? 'Cancelando...' : 'Cancelar exclusão'}
      </button>
    </div>
  )
}
