'use client'

import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import styles from './TrocaCena.module.css'

const SAIDA_MS = 560

/**
 * Troca suave entre "cenas" que se substituem no mesmo lugar (o checkout Pix:
 * QR → confirmando → confirmado). Diferente de {@link Transicao}, que só anima a MONTAGEM:
 * aqui a cena que sai também anima (desliza pra cima sumindo, enquanto a nova sobe e
 * aparece) e a altura do palco acompanha a cena nova em vez de dar um pulo seco quando o
 * conteúdo muda de tamanho (o QR é alto, a tela de "confirmando" é baixa).
 *
 * `renderCena(key)` é chamado pra CADA camada montada — a cena que está saindo continua
 * sendo renderizada de verdade (mesma instância, mesmo `key`) até o fim da animação, então
 * nada re-monta e não tem "piscada". Anima só quando `cenaKey` muda.
 */
export function TrocaCena({
  cenaKey,
  renderCena,
}: {
  cenaKey: string
  renderCena: (key: string) => ReactNode
}) {
  const [ativa, setAtiva] = useState(cenaKey)
  const [camadas, setCamadas] = useState<string[]>([cenaKey])
  const palcoRef = useRef<HTMLDivElement>(null)
  const alturaEstavel = useRef(0)

  // Ajuste de estado no render (padrão React p/ estado derivado de prop): entra a cena nova
  // e a anterior fica na lista pra animar a saída.
  if (ativa !== cenaKey) {
    setAtiva(cenaKey)
    setCamadas((prev) => (prev.includes(cenaKey) ? prev : [...prev, cenaKey]))
  }

  const emTransicao = camadas.length > 1

  // Fora de transição, memoriza a altura da cena atual — ponto de partida da animação de
  // altura na próxima troca.
  useLayoutEffect(() => {
    if (!emTransicao && palcoRef.current) alturaEstavel.current = palcoRef.current.offsetHeight
  })

  useLayoutEffect(() => {
    if (!emTransicao) return

    const palco = palcoRef.current
    if (palco) {
      palco.style.height = `${alturaEstavel.current}px`
      void palco.offsetHeight // reflow: fixa o "de" antes de animar
      palco.style.height = `${palco.scrollHeight}px`
    }

    const t = window.setTimeout(() => {
      setCamadas([ativa])
      if (palco) palco.style.height = ''
    }, SAIDA_MS)
    return () => window.clearTimeout(t)
    // Só reage à troca de cena.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ativa])

  return (
    <div ref={palcoRef} className={styles.palco}>
      {camadas.map((key) => {
        const ehAtiva = key === ativa
        return (
          <div
            key={key}
            className={ehAtiva ? (emTransicao ? styles.cenaEntra : undefined) : styles.cenaSai}
            aria-hidden={!ehAtiva}
          >
            {renderCena(key)}
          </div>
        )
      })}
    </div>
  )
}
