'use client'

import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import styles from './TrocaPasso.module.css'

const SAIDA_MS = 340

interface Camada {
  passo: 1 | 2 | 3
  no: ReactNode
  entrando: -1 | 1 | 0 // -1 entra da esquerda, 1 entra da direita, 0 = já assentada
}

/**
 * Troca entre as cenas do wizard de cadastro. Direcional: avançar desliza a cena atual pra
 * esquerda e traz a nova da direita; voltar faz o inverso. A altura do palco acompanha a
 * cena nova. Anima só quando `passo` muda. Espelha o padrão do <TrocaCena>, mas horizontal.
 */
export function TrocaPasso({
  passo,
  direcao,
  children,
}: {
  passo: 1 | 2 | 3
  direcao: 1 | -1
  children: ReactNode
}) {
  const [ativo, setAtivo] = useState<1 | 2 | 3>(passo)
  const [camadas, setCamadas] = useState<Camada[]>([{ passo, no: children, entrando: 0 }])
  const palcoRef = useRef<HTMLDivElement>(null)
  const alturaEstavel = useRef(0)

  // Ajuste de estado no render (estado derivado de prop): entra a cena nova, a antiga fica
  // na lista pra animar a saída.
  if (ativo !== passo) {
    setAtivo(passo)
    setCamadas((prev) => {
      const semDupe = prev
        .filter((c) => c.passo !== passo)
        .map((c) => ({ ...c, entrando: 0 as const }))
      return [...semDupe, { passo, no: children, entrando: direcao === 1 ? (1 as const) : (-1 as const) }]
    })
  } else {
    // Mesma cena, conteúdo pode ter mudado (erro de API apareceu): atualiza o nó da ativa.
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
      const alturaNova = palco.scrollHeight // cena nova está em fluxo; a que sai é absolute
      const encolhe = alturaNova < alturaEstavel.current
      palco.style.height = `${alturaEstavel.current}px`
      void palco.offsetHeight // reflow: fixa o "de" antes de animar
      if (encolhe) {
        // Ao voltar pra um passo mais curto, segura a altura alta até a cena que sai quase
        // terminar de deslizar — senão o `overflow: hidden` corta o rodapé dela e fica
        // estranho (o avanço não tem isso porque o palco só cresce).
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
    // reage só à troca de cena
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
