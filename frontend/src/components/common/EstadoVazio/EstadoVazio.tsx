'use client'

import { type LucideIcon, SearchX } from 'lucide-react'
import styles from './EstadoVazio.module.css'

interface AcaoBotao {
  label: string
  onClick: () => void
}

interface EstadoVazioProps {
  icone?: LucideIcon
  titulo: string
  mensagem?: string
  acaoPrimaria?: AcaoBotao
  acaoSecundaria?: AcaoBotao
  /** Menos padding e ícone menor — pra usar dentro de modal/drawer, não como tela inteira. */
  compacta?: boolean
}

export function EstadoVazio({
  icone: Icone = SearchX,
  titulo,
  mensagem,
  acaoPrimaria,
  acaoSecundaria,
  compacta = false,
}: EstadoVazioProps) {
  return (
    <div className={`${styles.container} ${compacta ? styles.compacta : ''}`}>
      <div className={styles.iconeWrap}>
        <Icone size={compacta ? 24 : 32} strokeWidth={1.5} />
      </div>
      <h3 className={styles.titulo}>{titulo}</h3>
      {mensagem && <p className={styles.mensagem}>{mensagem}</p>}
      {(acaoPrimaria || acaoSecundaria) && (
        <div className={styles.acoes}>
          {acaoSecundaria && (
            <button className={styles.botaoSecundario} onClick={acaoSecundaria.onClick}>
              {acaoSecundaria.label}
            </button>
          )}
          {acaoPrimaria && (
            <button className={styles.botaoPrimario} onClick={acaoPrimaria.onClick}>
              {acaoPrimaria.label}
            </button>
          )}
        </div>
      )}
    </div>
  )
}