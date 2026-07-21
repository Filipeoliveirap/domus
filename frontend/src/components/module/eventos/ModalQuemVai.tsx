'use client'

import { useEffect } from 'react'
import { X, Users } from 'lucide-react'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useListaInscritos } from '@/hooks/inscricao/useListaInscritos'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useAuthStore } from '@/store/authStore'
import { iniciais } from '@/lib/formats/membroFormat'
import styles from './ModalQuemVai.module.css'

interface Props {
  eventoId: string
  aoFechar: () => void
}

/**
 * Quem vai ao evento, aberto ao clicar na pilha de avatares.
 *
 * <p>Duas fontes por papel, e a diferença é de privacidade, não de conveniência:
 * o membro recebe a lista <b>reduzida</b> (nome e foto); ADMIN/LÍDER recebem a completa,
 * que inclui telefone de convidado e quem inscreveu quem. Disparar a consulta de admin
 * para um membro devolveria 401 — por isso cada uma só roda para quem tem direito.
 */
export function ModalQuemVai({ eventoId, aoFechar }: Props) {
  const role = useAuthStore((s) => s.role)
  const ehGestor = role === 'ADMIN_IGREJA' || role === 'LIDER'

  const { data: participantes = [], isLoading: carregandoLista } = useParticipantes(
    eventoId, !ehGestor)
  const { data: listaAdmin, isLoading: carregandoAdmin } = useListaInscritos(
    eventoId, ehGestor)
  const cancelar = useCancelarInscricao()

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') aoFechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aoFechar])

  // Normaliza as duas formas numa só, para a marcação não se ramificar por papel.
  const linhas = ehGestor
    ? (listaAdmin?.inscritos ?? []).map((i) => ({
        id: i.id,
        nome: i.nome,
        foto: i.foto,
        convidados: i.acompanhantes.map((a) => a.nome),
      }))
    : participantes.map((p) => ({
        id: p.id,
        nome: p.nome,
        foto: p.foto,
        convidados: p.convidados,
      }))

  const carregando = ehGestor ? carregandoAdmin : carregandoLista
  const total = linhas.reduce((acc, l) => acc + 1 + l.convidados.length, 0)

  return (
    <div className={styles.overlay} onMouseDown={aoFechar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-quem-vai"
      >
        <header className={styles.cabecalho}>
          <div>
            <h2 className={styles.titulo} id="titulo-quem-vai">
              Quem vai
            </h2>
            <p className={styles.subtitulo}>
              {total === 1 ? '1 pessoa confirmada' : `${total} pessoas confirmadas`}
            </p>
          </div>
          <button type="button" className={styles.fechar} onClick={aoFechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </header>

        <div className={styles.lista}>
          {carregando ? (
            <p className={styles.estado}>Carregando…</p>
          ) : linhas.length === 0 ? (
            <div className={styles.vazio}>
              <Users size={28} aria-hidden="true" />
              <p>Ninguém confirmou ainda.</p>
            </div>
          ) : (
            linhas.map((l) => (
              <div key={l.id} className={styles.grupo}>
                <div className={styles.linha}>
                  <span className={styles.avatar}>
                    {l.foto ? (
                      // eslint-disable-next-line @next/next/no-img-element -- URL de storage externo
                      <img src={l.foto} alt="" className={styles.avatarFoto} />
                    ) : (
                      iniciais(l.nome)
                    )}
                  </span>
                  <span className={styles.nome}>{l.nome}</span>

                  {ehGestor && (
                    <button
                      type="button"
                      className={styles.cancelar}
                      onClick={() => cancelar.mutate(l.id)}
                      disabled={cancelar.isPending}
                    >
                      Cancelar inscrição
                    </button>
                  )}
                </div>

                {/* Convidado aninhado sob quem o trouxe: é o que responde "de onde veio". */}
                {l.convidados.map((nome, i) => (
                  <div key={`${l.id}-${i}`} className={styles.convidado}>
                    <span className={styles.avatarConvidado}>{iniciais(nome)}</span>
                    <span className={styles.nomeConvidado}>{nome}</span>
                    <span className={styles.selo}>Convidado</span>
                  </div>
                ))}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
