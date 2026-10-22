'use client'

import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import styles from './TrocaPasso.module.css'

const SAIDA_MS = 340

interface Camada {
  passo: 1 | 2 | 3 | 4
  no: ReactNode
  entrando: -1 | 1 | 0
}

export function TrocaPasso({
  passo,
  direcao,
  children,
}: {
  passo: 1 | 2 | 3 | 4
  direcao: 1 | -1
  children: ReactNode
}) {
  const [ativo, setAtivo] = useState<1 | 2 | 3 | 4>(passo)
  const [camadas, setCamadas] = useState<Camada[]>([{ passo, no: children, entrando: 0 }])
  const palcoRef = useRef<HTMLDivElement>(null)
  const alturaEstavel = useRef(0)

  if (ativo !== passo) {
    setAtivo(passo)
    setCamadas((prev) => {
      const semDupe = prev
        .filter((c) => c.passo !== passo)
        .map((c) => ({ ...c, entrando: 0 as const }))
      return [...semDupe, { passo, no: children, entrando: direcao === 1 ? (1 as const) : (-1 as const) }]
    })
  } else {
    const ativaAtual = camadas.find((c) => c.passo === passo && c.entrando === 0)
    if (ativaAtual && ativaAtual.no !== children) {
      setCamadas((prev) => prev.map((c) => (c === ativaAtual ? { ...c, no: children } : c)))
    }
  }

  const emTransicao = camadas.length > 1

  useLayoutEffect(() => {
    if (!emTransicao && palcoRef.current) alturaEstavel.current = palcoRef.current.offsetHeight
  })

  useLayoutEffect(() => {
    if (!emTransicao) return
    const palco = palcoRef.current
    const timers: number[] = []
    if (palco) {
      const alturaNova = palco.scrollHeight
      const encolhe = alturaNova < alturaEstavel.current
      palco.style.height = `${alturaEstavel.current}px`
      void palco.offsetHeight
      if (encolhe) {
        timers.push(window.setTimeout(() => { palco.style.height = `${alturaNova}px` }, 150))
      } else {
        palco.style.height = `${alturaNova}px`
      }
    }
    timers.push(
      window.setTimeout(() => {
        setCamadas((prev) =>
          prev.filter((c) => c.passo === ativo).map((c) => ({ ...c, entrando: 0 as const })),
        )
      }, SAIDA_MS),
    )
    timers.push(
      window.setTimeout(() => {
        if (palcoRef.current) palcoRef.current.style.height = ''
      }, SAIDA_MS + 200),
    )
    return () => timers.forEach(window.clearTimeout)
  }, [ativo])

  return (
    <div ref={palcoRef} className={styles.palco}>
      {camadas.map((c) => {
        const saindo = c.passo !== ativo
        const cls = [
          styles.cena,
          saindo ? (direcao === 1 ? styles.saiEsquerda : styles.saiDireita) : '',
          !saindo && c.entrando === 1 ? styles.entraDireita : '',
          !saindo && c.entrando === -1 ? styles.entraEsquerda : '',
        ]
          .filter(Boolean)
          .join(' ')
        return (
          <div key={c.passo} className={cls} aria-hidden={saindo}>
            {c.no}
          </div>
        )
      })}
    </div>
  )
}
