'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import { useUiStore } from '@/store/uiStore'
import { useAuthStore } from '@/store/authStore'
import styles from './PortalBoasVindas.module.css'

/**
 * Véu de boas-vindas por cima do app shell quando ele aparece. Reusa a linguagem de vidro
 * fosco do <PonteParaCheckout>/<loading> e termina como a transição eventos→checkout: o véu
 * inteiro sobe (cortina) e descobre o conteúdo, que já carregou por trás.
 *
 * `'completa'` (depois de logar): logo + "Bem-vindo, {nome}" + barra de progresso, ~1,8s.
 * `'curta'` (voltar com sessão ativa, 1×/sessão): só o logo em fade + cortina, ~0,8s.
 * `prefers-reduced-motion`: sem véu (tempos zerados + `transition: none` no CSS).
 */
const TEMPOS = {
  completa: { espera: 1150, cortina: 640 },
  curta: { espera: 260, cortina: 520 },
} as const

export function PortalBoasVindas() {
  const tipo = useUiStore((s) => s.boasVindas)
  const encerrar = useUiStore((s) => s.encerrarBoasVindas)
  const nome = useAuthStore((s) => s.nome)

  const [fase, setFase] = useState<'oculto' | 'entrada' | 'cortina'>('oculto')
  // Congela o tipo pro conteúdo continuar certo enquanto a cortina sobe (o store já zerou).
  const [tipoCongelado, setTipoCongelado] = useState<'completa' | 'curta' | null>(null)

  // Arranque em render (mesmo padrão de ajuste-de-estado-na-troca-de-prop do projeto):
  // quando o store pede a animação e nada está rodando, entra na fase de entrada.
  if (tipo && fase === 'oculto') {
    setFase('entrada')
    setTipoCongelado(tipo)
  }

  // Sair do app shell no meio da animação (ex.: entrar no checkout, que é rota fora do
  // shell) desmonta este componente. Sem isto, `boasVindas` ficaria armado no store e a
  // animação de login re-tocaria ao voltar pro shell — só deve tocar em login/retorno real.
  useEffect(() => {
    return () => { useUiStore.getState().encerrarBoasVindas() }
  }, [])

  useEffect(() => {
    if (fase !== 'entrada') return
    const reduzMovimento = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const { espera, cortina } = reduzMovimento
      ? { espera: 0, cortina: 0 }
      : TEMPOS[tipoCongelado ?? 'curta']

    const t1 = window.setTimeout(() => setFase('cortina'), espera)
    const t2 = window.setTimeout(() => {
      setFase('oculto')
      setTipoCongelado(null)
      encerrar()
    }, espera + cortina)

    return () => {
      window.clearTimeout(t1)
      window.clearTimeout(t2)
    }
  }, [fase, tipoCongelado, encerrar])

  if (fase === 'oculto') return null

  const ehCompleta = tipoCongelado === 'completa'
  const primeiroNome = nome?.trim().split(/\s+/)[0]

  return (
    <div className={clsx(styles.veu, fase === 'cortina' && styles.subindo)} aria-hidden="true">
      <div className={styles.conteudo}>
        {/* eslint-disable-next-line @next/next/no-img-element -- asset estático local */}
        <img src="/images/logo2.png" alt="" className={styles.logo} />
        {ehCompleta && (
          <p className={styles.saudacao}>
            {primeiroNome ? (
              <>
                Bem-vindo, <strong>{primeiroNome}</strong>
              </>
            ) : (
              'Bem-vindo ao Domus'
            )}
          </p>
        )}
        {ehCompleta && <span className={styles.barra} aria-hidden="true" />}
      </div>
    </div>
  )
}
