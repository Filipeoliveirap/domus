'use client'

import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { ClipboardList } from 'lucide-react'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './ModalRespostasInscrito.module.css'

/** Só leitura — visão do organizador sobre as respostas de um inscrito qualquer aos
 *  campos personalizados do evento. Diferente de RespostasCamposPersonalizados (que é o
 *  fluxo do próprio titular respondendo/editando as suas), aqui não há edição.
 *  Abre por cima de ModalDetalheInscrito, por isso o z-index maior no CSS. */
export function ModalRespostasInscrito({
  nome, inscricaoId, onClose,
}: { nome: string; inscricaoId: string; onClose: () => void }) {
  const { data: respostas, isPending } = useRespostasCampos(inscricaoId)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  if (typeof document === 'undefined') return null

  return createPortal(
    <div className={`${baseStyles.overlay} ${styles.overlaySobreposto}`} onMouseDown={onClose}>
      <div
        className={baseStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-respostas-inscrito-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <ClipboardList size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-respostas-inscrito-titulo">
            Respostas de {nome}
          </h2>
        </div>

        <div className={`${baseStyles.corpo} ${styles.corpo}`}>
          {isPending ? (
            <p>Carregando…</p>
          ) : !respostas || respostas.length === 0 ? (
            <p>Nenhuma resposta enviada ainda.</p>
          ) : (
            <dl className={styles.lista}>
              {respostas.map((r) => (
                <div key={r.campoId} className={styles.item}>
                  <dt className={styles.pergunta}>{r.label}</dt>
                  <dd className={styles.resposta}>
                    {r.valor?.trim()
                      ? (r.tipo === 'MULTIPLA_ESCOLHA'
                        ? r.valor.split(' | ').filter(Boolean).join(', ')
                        : r.valor)
                      : <span className={styles.semResposta}>Não respondido</span>}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </div>

        <div className={baseStyles.rodape}>
          <button type="button" className={baseStyles.btnCancelar} onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
