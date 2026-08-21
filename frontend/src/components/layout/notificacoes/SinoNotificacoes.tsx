'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Bell } from 'lucide-react'
import { useContagemNaoLidas } from '@/hooks/notificacoes/useContagemNaoLidas'
import { useListaNotificacoes } from '@/hooks/notificacoes/useListaNotificacoes'
import { useMarcarNotificacaoLida, useMarcarTodasNotificacoesLidas } from '@/hooks/notificacoes/useMarcarNotificacaoLida'
import { useNotificacoesSSE } from '@/hooks/notificacoes/useNotificacoesSSE'
import type { NotificacaoCentral } from '@/types/notificacaoCentral.type'
import styles from './SinoNotificacoes.module.css'

export function SinoNotificacoes() {
  const [aberto, setAberto] = useState(false)
  const wrapperRef = useRef<HTMLDivElement>(null)
  const router = useRouter()

  useNotificacoesSSE()

  const { data: totalNaoLidas } = useContagemNaoLidas()
  const { data: pagina, isLoading } = useListaNotificacoes(aberto)
  const marcarLida = useMarcarNotificacaoLida()
  const marcarTodasLidas = useMarcarTodasNotificacoesLidas()

  useEffect(() => {
    function handleClickFora(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setAberto(false)
      }
    }
    document.addEventListener('mousedown', handleClickFora)
    return () => document.removeEventListener('mousedown', handleClickFora)
  }, [])

  function clicarNotificacao(n: NotificacaoCentral) {
    if (!n.lida) marcarLida.mutate(n.id)
    setAberto(false)
    if (n.link) router.push(n.link)
  }

  const notificacoes = pagina?.content ?? []
  const contador = totalNaoLidas ?? 0

  return (
    <div className={styles.wrapper} ref={wrapperRef}>
      <button
        type="button"
        className={styles.botaoSino}
        onClick={() => setAberto((v) => !v)}
        aria-label={contador > 0 ? `Notificações, ${contador} não lidas` : 'Notificações'}
      >
        <Bell size={20} />
        {contador > 0 && <span className={styles.badge}>{contador > 9 ? '9+' : contador}</span>}
      </button>

      {aberto && (
        <div className={styles.dropdown}>
          <div className={styles.cabecalho}>
            <span>Notificações</span>
            {notificacoes.length > 0 && (
              <button
                type="button"
                className={styles.marcarTodas}
                onClick={() => marcarTodasLidas.mutate()}
              >
                Marcar todas como lidas
              </button>
            )}
          </div>

          {isLoading && <p className={styles.vazio}>Carregando…</p>}
          {!isLoading && notificacoes.length === 0 && (
            <p className={styles.vazio}>Nenhuma notificação por aqui.</p>
          )}

          <ul className={styles.lista}>
            {notificacoes.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  className={`${styles.item} ${n.lida ? styles.itemLido : ''}`}
                  onClick={() => clicarNotificacao(n)}
                >
                  {n.texto}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
