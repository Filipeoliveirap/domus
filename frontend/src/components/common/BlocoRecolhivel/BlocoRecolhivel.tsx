'use client'

import { useEffect, useId, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import styles from './BlocoRecolhivel.module.css'

interface BlocoRecolhivelProps {
  /** id estável — usado no aria e no evento "abrir por causa de erro". */
  id: string
  titulo: string
  descricao?: string
  icone?: React.ReactNode
  /** Abre já expandido (ex.: edição de evento que já tem restrição ativa). */
  defaultAberto?: boolean
  children: React.ReactNode
}

/**
 * Disclosure: cabeçalho clicável + corpo que expande suave. Para conteúdo
 * opcional que a maioria dos eventos não usa (recorrência, restrição de
 * público, campos personalizados) — some do caminho até alguém pedir.
 *
 * Diferente do <Revelar> (que é controlado por um booleano externo, tipo
 * `{toggle && <Revelar>}`): aqui o estado aberto/fechado é do próprio bloco.
 *
 * O useRolarParaErro dispara `domus:abrir-recolhivel` com o id deste bloco
 * quando há um campo com erro escondido aqui dentro.
 */
export function BlocoRecolhivel({
  id, titulo, descricao, icone, defaultAberto = false, children,
}: BlocoRecolhivelProps) {
  const [aberto, setAberto] = useState(defaultAberto)
  const corpoId = `recolhivel-${id}`
  const tituloId = useId()

  useEffect(() => {
    function aoAbrirPorErro(e: Event) {
      const detail = (e as CustomEvent<{ id: string }>).detail
      if (detail?.id === id) setAberto(true)
    }
    window.addEventListener('domus:abrir-recolhivel', aoAbrirPorErro)
    return () => window.removeEventListener('domus:abrir-recolhivel', aoAbrirPorErro)
  }, [id])

  return (
    <div className={styles.bloco}>
      <button
        type="button"
        className={styles.cabecalho}
        aria-expanded={aberto}
        aria-controls={corpoId}
        onClick={() => setAberto((v) => !v)}
      >
        <ChevronRight
          size={18}
          className={`${styles.chevron} ${aberto ? styles.chevronAberto : ''}`}
          aria-hidden="true"
        />
        {icone && <span className={styles.icone} aria-hidden="true">{icone}</span>}
        <span className={styles.textos}>
          <span className={styles.titulo} id={tituloId}>{titulo}</span>
          {descricao && <span className={styles.descricao}>{descricao}</span>}
        </span>
      </button>

      <div
        id={corpoId}
        role="region"
        aria-labelledby={tituloId}
        hidden={!aberto}
        data-recolhivel
        data-id={id}
        className={styles.corpoWrap}
      >
        <div className={styles.corpoInner}>{children}</div>
      </div>
    </div>
  )
}
