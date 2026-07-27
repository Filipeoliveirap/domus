'use client'

import { toast as sonner } from 'sonner'
import { CheckCircle2, XCircle, AlertTriangle, Info, X } from 'lucide-react'
import type { ReactNode } from 'react'
import styles from './Notificacao.module.css'

type Tipo = 'sucesso' | 'erro' | 'aviso' | 'info'

const ICONES: Record<Tipo, typeof CheckCircle2> = {
  sucesso: CheckCircle2,
  erro: XCircle,
  aviso: AlertTriangle,
  info: Info,
}

const DURACAO: Record<Tipo, number> = {
  sucesso: 4000,
  erro: 7000,
  aviso: 6000,
  info: 5000,
}

function Corpo({
  tipo,
  titulo,
  descricao,
  aoFechar,
}: {
  tipo: Tipo
  titulo: ReactNode
  descricao?: ReactNode
  aoFechar: () => void
}) {
  const Icone = ICONES[tipo]

  return (
    <div className={`${styles.toast} ${styles[tipo]}`}>
      <div className={styles.icone}>
        <Icone size={20} aria-hidden="true" />
      </div>

      <div className={styles.conteudo}>
        <span className={styles.titulo}>{titulo}</span>
        {descricao && <span className={styles.descricao}>{descricao}</span>}
      </div>

      <button type="button" className={styles.fechar} onClick={aoFechar} aria-label="Fechar aviso">
        <X size={16} />
      </button>
    </div>
  )
}

function criar(tipo: Tipo) {
  return (titulo: ReactNode, descricao?: ReactNode) =>
    sonner.custom(
      (id) => <Corpo tipo={tipo} titulo={titulo} descricao={descricao} aoFechar={() => sonner.dismiss(id)} />,
      { duration: DURACAO[tipo] },
    )
}

export const notificar = {
  sucesso: criar('sucesso'),
  erro: criar('erro'),
  aviso: criar('aviso'),
  info: criar('info'),
}
