'use client'

import { useEffect, useRef } from 'react'
import { Repeat } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './ModalEscopoEdicaoEvento.module.css'
import type { EscopoEdicaoEvento } from '@/types/evento.type'

type AcaoSerie = 'editar' | 'arquivar' | 'restaurar'

interface Props {
  titulo: string
  /** Define a pergunta e a descrição de cada opção conforme a ação. */
  acao?: AcaoSerie
  onEscolher: (escopo: EscopoEdicaoEvento) => void
  onClose: () => void
}

/** Rótulo fixo de cada opção — não muda conforme a ação, só a descrição muda. */
const ROTULOS: Record<EscopoEdicaoEvento, string> = {
  ESTA: 'Só este encontro',
  ESTA_E_SEGUINTES: 'Este e os próximos',
  SERIE: 'A série inteira',
}

const COPY: Record<AcaoSerie, { pergunta: string } & Record<EscopoEdicaoEvento, string>> = {
  editar: {
    pergunta: 'O que você quer alterar?',
    ESTA: 'As outras datas da série continuam como estão.',
    ESTA_E_SEGUINTES: 'Vale deste encontro em diante. Os que já aconteceram não mudam.',
    SERIE: 'Todos os encontros, inclusive os que já aconteceram.',
  },
  arquivar: {
    pergunta: 'O que você quer arquivar?',
    ESTA: 'As outras datas da série continuam na agenda.',
    ESTA_E_SEGUINTES: 'Arquiva deste encontro em diante. Os que já aconteceram ficam.',
    SERIE: 'Arquiva todos os encontros e encerra a série.',
  },
  restaurar: {
    pergunta: 'O que você quer restaurar?',
    ESTA: 'As outras datas arquivadas continuam arquivadas.',
    ESTA_E_SEGUINTES: 'Restaura deste encontro em diante.',
    SERIE: 'Restaura todos os encontros arquivados, inclusive os que já passaram.',
  },
}

const ORDEM: EscopoEdicaoEvento[] = ['ESTA', 'ESTA_E_SEGUINTES', 'SERIE']

/** Esta ocorrência faz parte de uma série recorrente — pergunta o alcance antes de
 *  editar/arquivar/restaurar, igual qualquer calendário maduro (Google Agenda) resolve.
 *  Cada opção tem um sentido fixo e igual nas três ações: só esta / desta data em diante /
 *  a série toda incluindo o passado. */
export function ModalEscopoEdicaoEvento({ titulo, acao = 'editar', onEscolher, onClose }: Props) {
  const primeiraRef = useRef<HTMLButtonElement>(null)
  const { saindo, fechar } = useFecharAnimado(onClose)
  const copy = COPY[acao]

  useEffect(() => {
    primeiraRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  return (
    <div
      className={clsx(baseStyles.overlay, saindo && baseStyles.saindo)}
      onMouseDown={() => fechar()}
    >
      <div
        className={baseStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-escopo-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <Repeat size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-escopo-titulo">
            &quot;{titulo}&quot; se repete
          </h2>
        </div>

        <div className={baseStyles.corpo}>
          <p>{copy.pergunta}</p>
        </div>

        <div className={styles.opcoes}>
          {ORDEM.map((escopo, i) => (
            <button
              key={escopo}
              ref={i === 0 ? primeiraRef : undefined}
              type="button"
              className={styles.opcao}
              onClick={() => onEscolher(escopo)}
            >
              <span className={styles.opcaoTitulo}>{ROTULOS[escopo]}</span>
              <span className={styles.opcaoDesc}>{copy[escopo]}</span>
            </button>
          ))}
        </div>

        <div className={baseStyles.rodape}>
          <button type="button" className={baseStyles.btnCancelar} onClick={fechar}>
            Cancelar
          </button>
        </div>
      </div>
    </div>
  )
}
