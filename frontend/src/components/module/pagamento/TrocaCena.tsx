'use client'

import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import styles from './TrocaCena.module.css'

/**
 * Troca suave entre "cenas" que se substituem no mesmo lugar (o checkout Pix:
 * QR → confirmando → confirmado). Diferente de {@link Transicao}, que só anima a MONTAGEM,
 * aqui a cena que sai também anima — crossfade curto — e a altura do palco acompanha a cena
 * nova em vez de dar um pulo seco quando o conteúdo muda de tamanho (o QR é alto, a tela de
 * "confirmando" é baixa). Anima só quando `cenaKey` muda.
 *
 * A cena que sai é um snapshot do conteúdo de quando aquela `cenaKey` entrou — some em
 * ~0,3s, então diferenças de estado interno (ex.: contador do Pix) não chegam a aparecer.
 */
export function TrocaCena({ cenaKey, children }: { cenaKey: string; children: ReactNode }) {
  // `vigente` guarda o par (key, node) da cena no ar. `node` só é reamostrado quando a
  // `cenaKey` muda — então, no instante da troca, o `vigente` anterior É a cena que sai.
  const [vigente, setVigente] = useState<{ key: string; node: ReactNode }>({ key: cenaKey, node: children })
  const [saindo, setSaindo] = useState<{ key: string; node: ReactNode } | null>(null)
  const palcoRef = useRef<HTMLDivElement>(null)
  const alturaEstavel = useRef(0)

  // Ajuste de estado durante o render (padrão React p/ estado derivado de prop): detecta a
  // troca de cena sem effect nem ref no render.
  if (vigente.key !== cenaKey) {
    setSaindo(vigente)
    setVigente({ key: cenaKey, node: children })
  }

  // Fora de transição, memoriza a altura da cena atual — ponto de partida da animação de
  // altura na próxima troca.
  useLayoutEffect(() => {
    if (!saindo && palcoRef.current) alturaEstavel.current = palcoRef.current.offsetHeight
  })

  // Anima a altura do palco (cena antiga → nova) e limpa o snapshot da cena que saiu.
  useLayoutEffect(() => {
    if (!saindo) return
    const palco = palcoRef.current
    if (palco) {
      palco.style.height = `${alturaEstavel.current}px`
      void palco.offsetHeight // reflow: fixa o "de" antes de animar
      palco.style.height = `${palco.scrollHeight}px`
    }
    const t = window.setTimeout(() => {
      setSaindo(null)
      if (palco) palco.style.height = ''
    }, 340)
    return () => window.clearTimeout(t)
  }, [saindo])

  return (
    <div ref={palcoRef} className={styles.palco}>
      {saindo && (
        <div key={saindo.key} className={styles.cenaSai} aria-hidden="true">
          {saindo.node}
        </div>
      )}
      <div key={vigente.key} className={saindo ? styles.cenaEntra : undefined}>
        {children}
      </div>
    </div>
  )
}
