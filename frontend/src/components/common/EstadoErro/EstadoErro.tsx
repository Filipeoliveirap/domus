'use client'

import { WifiOff, RefreshCw } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import styles from './EstadoErro.module.css'

interface EstadoErroProps {
  icone?: LucideIcon
  titulo?: string
  mensagem?: string
  aoTentarNovamente?: () => void
}

export function EstadoErro({
  icone: Icone = WifiOff,
  titulo = 'Erro de conexão',
  mensagem = 'Não foi possível carregar os dados. Verifique sua conexão e tente novamente.',
  aoTentarNovamente,
}: EstadoErroProps) {
  return (
    <div className={styles.container}>
      <span className={styles.iconeBox}>
        <Icone size={32} />
      </span>
      <h3 className={styles.titulo}>{titulo}</h3>
      <p className={styles.mensagem}>{mensagem}</p>
      {aoTentarNovamente && (
        <button className={styles.botao} onClick={aoTentarNovamente}>
          <RefreshCw size={16} />
          Tentar novamente
        </button>
      )}
    </div>
  )
}