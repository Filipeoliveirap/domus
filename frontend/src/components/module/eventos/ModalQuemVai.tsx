'use client'

import { useEffect, useState } from 'react'
import { X, Users } from 'lucide-react'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useListaInscritos } from '@/hooks/inscricao/useListaInscritos'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useAuthStore } from '@/store/authStore'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import type { SituacaoEvento } from '@/types/evento.type'
import styles from './ModalQuemVai.module.css'

interface Props {
  eventoId: string
  situacao: SituacaoEvento
  restritoPropriaIgreja?: boolean
  aoFechar: () => void
}

export function ModalQuemVai({ eventoId, situacao, restritoPropriaIgreja, aoFechar }: Props) {
  const role = useAuthStore((s) => s.role)
  const ehGestor = podeGerenciarInscricoes(role)
  const podeCancelar = podeCancelarInscricao(situacao)

  const { data: participantes = [], isLoading: carregandoLista } = useParticipantes(
    eventoId, !ehGestor)
  // size=500: "quem vai" mostra todos de uma vez, não pagina
  const { data: listaAdmin, isLoading: carregandoAdmin } = useListaInscritos(
    eventoId, ehGestor, '', 0, 500)
  const cancelar = useCancelarInscricao()
  const [confirmandoId, setConfirmandoId] = useState<string | null>(null)
  const [cancelandoComConvidados, setCancelandoComConvidados] =
    useState<{ id: string; nome: string; quantidadeConvidados: number } | null>(null)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') aoFechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aoFechar])

  // Normaliza as duas fontes numa só, para não ramificar a marcação por papel
  const linhas = ehGestor
    ? (listaAdmin?.inscritos.content ?? []).map((i) => ({
        id: i.id,
        nome: i.nome,
        fotoId: i.fotoId,
        convidados: i.acompanhantes.map((a) => a.nome),
        igrejaDaPessoa: i.igrejaDaPessoa,
      }))
    : participantes.map((p) => ({
        id: p.id,
        nome: p.nome,
        fotoId: p.fotoId,
        convidados: p.convidados,
        igrejaDaPessoa: p.igrejaDaPessoa,
      }))

  const carregando = ehGestor ? carregandoAdmin : carregandoLista
  const total = linhas.reduce((acc, l) => acc + 1 + l.convidados.length, 0)

  const igrejasDistintas = new Set(linhas.map((l) => l.igrejaDaPessoa?.id).filter(Boolean))
  const mostrarIgreja = !restritoPropriaIgreja || igrejasDistintas.size > 1

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
                    {urlFoto(l.fotoId, 'THUMB') ? (
                      // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                      <img src={urlFoto(l.fotoId, 'THUMB')!} alt="" className={styles.avatarFoto} />
                    ) : (
                      iniciais(l.nome)
                    )}
                  </span>
                  <span className={styles.nome}>{l.nome}</span>

                  {mostrarIgreja && l.igrejaDaPessoa && (
                    <span className={styles.selo}>
                      {l.igrejaDaPessoa.sigla ?? l.igrejaDaPessoa.nome}
                    </span>
                  )}

                  {ehGestor && !podeCancelar && (
                    <span className={styles.selo}>Participou</span>
                  )}

                  {ehGestor && podeCancelar && (
                    confirmandoId === l.id ? (
                      <span className={styles.confirmacao}>
                        <span className={styles.confirmacaoTexto}>Cancelar?</span>
                        <button
                          type="button"
                          className={styles.confirmarSim}
                          onClick={() => {
                            cancelar.mutate(l.id, { onSuccess: () => setConfirmandoId(null) })
                          }}
                          disabled={cancelar.isPending}
                        >
                          Sim
                        </button>
                        <button
                          type="button"
                          className={styles.confirmarNao}
                          onClick={() => setConfirmandoId(null)}
                        >
                          Não
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        className={styles.cancelar}
                        onClick={() => {
                          // Sem convidado: confirmação inline. Com convidado: cancela junto e exige digitar o nome.
                          if (l.convidados.length > 0) {
                            setCancelandoComConvidados({
                              id: l.id, nome: l.nome, quantidadeConvidados: l.convidados.length,
                            })
                          } else {
                            setConfirmandoId(l.id)
                          }
                        }}
                        disabled={cancelar.isPending}
                      >
                        Cancelar inscrição
                      </button>
                    )
                  )}
                </div>

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

      {cancelandoComConvidados && (
        <ConfirmarCancelamentoInscricao
          nome={cancelandoComConvidados.nome}
          proprio={false}
          quantidadeConvidados={cancelandoComConvidados.quantidadeConvidados}
          isLoading={cancelar.isPending}
          onConfirmar={() => {
            cancelar.mutate(cancelandoComConvidados.id, {
              onSuccess: () => setCancelandoComConvidados(null),
            })
          }}
          onClose={() => setCancelandoComConvidados(null)}
        />
      )}
    </div>
  )
}
