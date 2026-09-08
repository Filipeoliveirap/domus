'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import Image from 'next/image'
import { X, Users } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useListaInscritos } from '@/hooks/inscricao/useListaInscritos'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useAuthStore } from '@/store/authStore'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import type { SituacaoEvento } from '@/types/evento.type'
import styles from './ModalQuemVai.module.css'

interface Props {
  eventoId: string
  situacao: SituacaoEvento
  /** Evento formal (inscrição) vs. só "marcar presença". Cancelar inscrição só aparece
   *  quando há inscrição de verdade — na lista de "quem vai" (presença) não faz sentido. */
  requerInscricao: boolean
  restritoPropriaIgreja?: boolean
  podeGerenciarEsteEvento: boolean
  aoFechar: () => void
}

export function ModalQuemVai({
  eventoId, situacao, requerInscricao, restritoPropriaIgreja, podeGerenciarEsteEvento, aoFechar,
}: Props) {
  const role = useAuthStore((s) => s.role)
  const ehGestor = podeGerenciarInscricoes(role) && podeGerenciarEsteEvento
  // Cancelar inscrição só num evento com inscrição, por quem gerencia, e só enquanto o
  // backend ainda aceita cancelamento.
  const podeCancelar = ehGestor && requerInscricao && podeCancelarInscricao(situacao)
  const eventoEncerrado = requerInscricao && !podeCancelarInscricao(situacao)

  const { data: participantes = [], isLoading: carregandoLista } = useParticipantes(eventoId, !ehGestor)
  // size=500: "quem vai" mostra todos de uma vez, não pagina
  const { data: listaAdmin, isLoading: carregandoAdmin } = useListaInscritos(eventoId, ehGestor, '', 0, 500)
  const cancelar = useCancelarInscricao()
  const [confirmandoId, setConfirmandoId] = useState<string | null>(null)
  const [fotoAberta, setFotoAberta] = useState<{ id: string; nome: string } | null>(null)
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  const linhas = ehGestor
    ? (listaAdmin?.inscritos.content ?? []).map((i) => ({
        id: i.id,
        nome: i.nome,
        fotoId: i.fotoId,
        igrejaDaPessoa: i.igrejaDaPessoa,
      }))
    : participantes.map((p) => ({
        id: p.id,
        nome: p.nome,
        fotoId: p.fotoId,
        igrejaDaPessoa: p.igrejaDaPessoa,
      }))

  const carregando = ehGestor ? carregandoAdmin : carregandoLista
  const total = linhas.length

  const igrejasDistintas = new Set(linhas.map((l) => l.igrejaDaPessoa?.id).filter(Boolean))
  const mostrarIgreja = !restritoPropriaIgreja || igrejasDistintas.size > 1

  return (
    <>
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      // Costuma abrir por cima do drawer/modal de detalhe do evento — sem parar a
      // propagação, o clique fora fecharia todos eles de uma vez.
      onMouseDown={(e) => {
        e.stopPropagation()
        fechar()
      }}
    >
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-quem-vai"
      >
        <span className={styles.grabber} aria-hidden="true" />
        <header className={styles.cabecalho}>
          <div>
            <h2 className={styles.titulo} id="titulo-quem-vai">
              Quem vai
            </h2>
            <p className={styles.subtitulo}>
              {total === 1 ? '1 pessoa confirmada' : `${total} pessoas confirmadas`}
            </p>
          </div>
          <button type="button" className={styles.fechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </header>

        <div className={styles.lista}>
          <Transicao key={carregando ? 'load' : linhas.length ? 'cheio' : 'vazio'} modo="fade">
          {carregando ? (
            <>
              {[0, 1, 2, 3, 4].map((i) => (
                <div key={i} className={styles.skeletonLinha}>
                  <span className={styles.skeletonAvatar} />
                  <span className={styles.skeletonNome} style={{ width: `${58 - i * 7}%` }} />
                </div>
              ))}
            </>
          ) : linhas.length === 0 ? (
            <div className={styles.vazio}>
              <Users size={28} aria-hidden="true" />
              <p>Ninguém confirmou ainda.</p>
            </div>
          ) : (
            linhas.map((l, i) => (
              <div key={l.id} className={styles.grupo} style={{ '--i': i } as React.CSSProperties}>
                <div className={styles.linha}>
                  {urlFoto(l.fotoId, 'THUMB') ? (
                    <button
                      type="button"
                      className={styles.avatar}
                      onClick={() => setFotoAberta({ id: l.fotoId!, nome: l.nome })}
                      aria-label={`Ver foto de ${l.nome}`}
                    >
                      <Image src={urlFoto(l.fotoId, 'THUMB')!} alt="" width={36} height={36} unoptimized className={styles.avatarFoto} />
                    </button>
                  ) : (
                    <span className={styles.avatar}>{iniciais(l.nome)}</span>
                  )}
                  <span className={styles.nome}>{l.nome}</span>

                  {mostrarIgreja && l.igrejaDaPessoa && (
                    <span className={styles.selo}>
                      {l.igrejaDaPessoa.sigla ?? l.igrejaDaPessoa.nome}
                    </span>
                  )}

                  {eventoEncerrado && ehGestor && (
                    <span className={styles.selo}>Participou</span>
                  )}

                  {podeCancelar && (
                    confirmandoId === l.id ? (
                      <span className={styles.confirmacao}>
                        <span className={styles.confirmacaoTexto}>Cancelar?</span>
                        <button
                          type="button"
                          className={styles.confirmarSim}
                          onClick={() => cancelar.mutate(l.id, { onSuccess: () => setConfirmandoId(null) })}
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
                        onClick={() => setConfirmandoId(l.id)}
                        disabled={cancelar.isPending}
                      >
                        Cancelar inscrição
                      </button>
                    )
                  )}
                </div>
              </div>
            ))
          )}
          </Transicao>
        </div>
      </div>
    </div>

    {/* Irmão do overlay: dentro dele, o clique pra fechar a foto fecharia o modal junto. */}
    {fotoAberta && (
      <VisualizadorFoto
        fotoId={fotoAberta.id}
        descricao={`Foto de ${fotoAberta.nome}`}
        onClose={() => setFotoAberta(null)}
      />
    )}
    </>
  )
}
