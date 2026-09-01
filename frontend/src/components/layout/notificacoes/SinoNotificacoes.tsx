'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useRouter } from 'next/navigation'
import { Bell } from 'lucide-react'
import { clsx } from 'clsx'
import { useContagemNaoLidas } from '@/hooks/notificacoes/useContagemNaoLidas'
import { useListaNotificacoes } from '@/hooks/notificacoes/useListaNotificacoes'
import { useMarcarNotificacaoLida, useMarcarTodasNotificacoesLidas } from '@/hooks/notificacoes/useMarcarNotificacaoLida'
import { useNotificacoesSSE } from '@/hooks/notificacoes/useNotificacoesSSE'
import type { NotificacaoCentral } from '@/types/notificacaoCentral.type'
import styles from './SinoNotificacoes.module.css'

export function SinoNotificacoes() {
  const [aberto, setAberto] = useState(false)
  const [saindo, setSaindo] = useState(false)
  const wrapperRef = useRef<HTMLDivElement>(null)
  const router = useRouter()

  useNotificacoesSSE()

  // fecha com a animação de saída em vez de sumir seco
  const fechar = useCallback(() => {
    setSaindo(true)
    setTimeout(() => {
      setAberto(false)
      setSaindo(false)
    }, 170)
  }, [])

  const { data: totalNaoLidas } = useContagemNaoLidas()
  const { data: pagina, isLoading } = useListaNotificacoes(aberto)
  const marcarLida = useMarcarNotificacaoLida()
  const marcarTodasLidas = useMarcarTodasNotificacoesLidas()

  useEffect(() => {
    if (!aberto) return
    function handleClickFora(e: MouseEvent) {
      const alvo = e.target as Node
      if (wrapperRef.current?.contains(alvo)) return
      if ((alvo as HTMLElement).closest?.(`.${styles.painel}`)) return
      fechar()
    }
    function handleEsc(e: KeyboardEvent) {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('mousedown', handleClickFora)
    document.addEventListener('keydown', handleEsc)
    return () => {
      document.removeEventListener('mousedown', handleClickFora)
      document.removeEventListener('keydown', handleEsc)
    }
  }, [aberto, fechar])

  function clicarNotificacao(n: NotificacaoCentral) {
    if (!n.lida) marcarLida.mutate(n.id)
    fechar()
    if (n.link) router.push(n.link)
  }

  const notificacoes = pagina?.content ?? []
  const contador = totalNaoLidas ?? 0

  const painel = (
    <>
      <div
        className={clsx(styles.backdrop, saindo && styles.saindo)}
        onClick={fechar}
        aria-hidden="true"
      />
      <div className={clsx(styles.painel, saindo && styles.saindo)} role="dialog" aria-label="Notificações">
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

        {isLoading && (
          <div className={styles.carregando} aria-label="Carregando notificações" aria-busy="true">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className={styles.skel} />
            ))}
          </div>
        )}
        {!isLoading && notificacoes.length === 0 && (
          <p className={styles.vazio}>Nenhuma notificação por aqui.</p>
        )}

        {!isLoading && notificacoes.length > 0 && (
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
        )}
      </div>
    </>
  )

  return (
    <div className={styles.wrapper} ref={wrapperRef}>
      <button
        type="button"
        className={styles.botaoSino}
        onClick={() => (aberto ? fechar() : setAberto(true))}
        aria-label={contador > 0 ? `Notificações, ${contador} não lidas` : 'Notificações'}
      >
        <Bell size={20} />
        {contador > 0 && <span className={styles.badge}>{contador > 9 ? '9+' : contador}</span>}
      </button>

      {aberto && typeof document !== 'undefined' && createPortal(painel, document.body)}
    </div>
  )
}
