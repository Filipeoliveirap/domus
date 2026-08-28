'use client'

import { createPortal } from 'react-dom'
import { AlertTriangle } from 'lucide-react'
import { useState, useEffect } from 'react'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './PendenciaCamposBadge.module.css'

/** Só renderiza algo quando falta responder pelo menos um campo obrigatório do evento —
 *  fica ao lado do nome na lista de inscritos, pra quem gerencia bater o olho e ver quem
 *  falta cobrar, sem abrir um por um. Clicar abre o detalhe de quais perguntas faltam
 *  (tooltip nativo não funciona em toque, então o modal é o jeito que também serve mobile). */
export function PendenciaCamposBadge({
  nome, inscricaoId, camposObrigatorios,
}: { nome: string; inscricaoId: string; camposObrigatorios: CampoPersonalizadoResponse[] }) {
  const { data: respostas } = useRespostasCampos(inscricaoId)
  const [aberto, setAberto] = useState(false)

  if (camposObrigatorios.length === 0 || !respostas) return null

  const respondidos = new Set(
    respostas.filter((r) => r.valor?.trim()).map((r) => r.campoId),
  )
  const pendentes = camposObrigatorios.filter((c) => !respondidos.has(c.id))

  if (pendentes.length === 0) return null

  return (
    <>
      <button
        type="button"
        className={styles.badge}
        onClick={(e) => { e.stopPropagation(); setAberto(true) }}
      >
        <AlertTriangle size={12} aria-hidden="true" />
        {pendentes.length === 1 ? '1 pendência' : `${pendentes.length} pendências`}
      </button>

      {aberto && (
        <ModalPendencias nome={nome} pendentes={pendentes} onClose={() => setAberto(false)} />
      )}
    </>
  )
}

function ModalPendencias({
  nome, pendentes, onClose,
}: { nome: string; pendentes: CampoPersonalizadoResponse[]; onClose: () => void }) {
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  if (typeof document === 'undefined') return null

  // e.stopPropagation() em todo clique daqui pra baixo: o portal renderiza no <body>,
  // mas continua descendente de PendenciaCamposBadge na árvore do React — e essa árvore
  // é o que decide bubbling de evento sintético, não o DOM real. Sem isso, qualquer
  // clique aqui dentro (fechar, backdrop) borbulha até o onClick da linha da tabela (que
  // fica ancestral desta badge) e reabre o modal de detalhe do inscrito por engano.
  return createPortal(
    <div
      className={baseStyles.overlay}
      onClick={(e) => { e.stopPropagation(); onClose() }}
    >
      <div
        className={baseStyles.modal}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-pendencias-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={`${baseStyles.iconBox} ${baseStyles.iconPerigo}`}>
            <AlertTriangle size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-pendencias-titulo">
            Pendências de {nome}
          </h2>
        </div>

        <div className={baseStyles.corpo}>
          Ainda falta responder {pendentes.length === 1 ? 'esta pergunta obrigatória' : 'estas perguntas obrigatórias'} do evento:
          <ul>
            {pendentes.map((c) => <li key={c.id}>{c.label}</li>)}
          </ul>
        </div>

        <div className={baseStyles.rodape}>
          <button
            type="button"
            className={baseStyles.btnCancelar}
            onClick={(e) => { e.stopPropagation(); onClose() }}
          >
            Fechar
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
