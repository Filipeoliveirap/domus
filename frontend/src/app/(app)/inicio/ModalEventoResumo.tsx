'use client'

import { useEffect, useState } from 'react'
import { X, CalendarDays, MapPin, Users, UserPlus } from 'lucide-react'
import { useEvento } from '@/hooks/evento/useEvento'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { iniciais } from '@/lib/formats/membroFormat'
import { BotaoConfirmarPresenca } from '@/components/module/eventos/BotaoConfirmarPresenca'
import { ModalInscreverMembros } from '@/components/module/eventos/ModalInscreverMembros'
import { ModalConvidado } from '@/components/module/eventos/ModalConvidado'
import styles from './ModalEventoResumo.module.css'

interface Props {
  eventoId: string
  aoFechar: () => void
}

const MAX_AVATARES = 3

function formatarQuando(inicioEm: string): string {
  const d = new Date(inicioEm)
  const data = d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }).replace('.', '')
  const hora = d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  return `${data}, ${hora}`
}

export function ModalEventoResumo({ eventoId, aoFechar }: Props) {
  const { data: evento, isLoading, isError } = useEvento(eventoId)
  const { data: participantes = [] } = useParticipantes(eventoId)
  const { data: minha } = useMinhaInscricao(eventoId)

  const [modalAberto, setModalAberto] = useState<'membros' | 'convidado' | null>(null)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') aoFechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [aoFechar])

  return (
    <div className={styles.overlay} onMouseDown={aoFechar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-evento"
      >
        <div className={styles.capa}>
          <button type="button" className={styles.fechar} onClick={aoFechar} aria-label="Fechar">
            <X size={18} />
          </button>

          {evento?.foto ? (
            // eslint-disable-next-line @next/next/no-img-element -- URL de storage externo; next/image exigiria configurar domínios
            <img src={evento.foto} alt="" className={styles.capaFoto} />
          ) : (
            <div className={styles.capaVazia} aria-hidden="true">
              <CalendarDays size={56} />
            </div>
          )}
        </div>

        {isLoading ? (
          <p className={styles.estado}>Carregando evento…</p>
        ) : isError || !evento ? (
          <p className={styles.estado}>Não foi possível carregar este evento.</p>
        ) : (
          <div className={styles.corpo}>
            <header>
              <h2 className={styles.titulo} id="titulo-evento">
                {evento.titulo}
              </h2>

              <div className={styles.metadados}>
                <span className={styles.chipData}>
                  <CalendarDays size={15} aria-hidden="true" />
                  {formatarQuando(evento.inicioEm)}
                </span>
                {evento.local && (
                  <span className={styles.local}>
                    <MapPin size={15} aria-hidden="true" />
                    {evento.local}
                  </span>
                )}
              </div>
            </header>

            <p className={`${styles.descricao} ${!evento.descricao ? styles.semDescricao : ''}`}>
              {evento.descricao || 'Este evento ainda não tem descrição.'}
            </p>

            {/*
              Bloco de presença: estrutura pronta para a inscrição em evento (Fase 2).
              Enquanto o endpoint não existe, `participantes` chega vazio e mostramos o
              estado honesto — em vez de um número inventado numa tela que a igreja lê como verdade.
            */}
            <section className={styles.presenca}>
              <div className={styles.presencaPessoas}>
                {participantes.length > 0 ? (
                  <>
                    <div className={styles.pilhaAvatares}>
                      {participantes.slice(0, MAX_AVATARES).map((p) => (
                        <span key={p.id} className={styles.avatarPresenca} title={p.nome}>
                          {p.foto ? (
                            // eslint-disable-next-line @next/next/no-img-element -- URL de storage externo
                            <img src={p.foto} alt="" className={styles.avatarPresencaFoto} />
                          ) : (
                            iniciais(p.nome)
                          )}
                        </span>
                      ))}
                    </div>
                    <span className={styles.presencaTexto}>
                      {participantes.length === 1
                        ? '1 pessoa confirmada'
                        : `${participantes.length} pessoas confirmadas`}
                    </span>
                  </>
                ) : (
                  <>
                    <span className={styles.avatarPresenca} aria-hidden="true">
                      <Users size={14} />
                    </span>
                    <span className={styles.presencaTexto}>
                      Confirmação de presença chega em breve.
                    </span>
                  </>
                )}
              </div>
            </section>

            <div className={styles.acoesInscricao}>
              <BotaoConfirmarPresenca
                eventoId={eventoId}
                inicioEm={evento.inicioEm}
                vagasRestantes={evento.vagas}
              />

              <button
                type="button"
                className={styles.acaoSecundaria}
                onClick={() => setModalAberto('membros')}
              >
                <Users size={16} aria-hidden="true" />
                Inscrever membros
              </button>

              {!evento.exclusivoMembros && minha?.inscrito && (
                <button
                  type="button"
                  className={styles.acaoSecundaria}
                  onClick={() => setModalAberto('convidado')}
                >
                  <UserPlus size={16} aria-hidden="true" />
                  Vou levar alguém de fora
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {modalAberto === 'membros' && (
        <ModalInscreverMembros
          eventoId={eventoId}
          tituloEvento={evento?.titulo ?? ''}
          exclusivoMembros={evento?.exclusivoMembros ?? false}
          onClose={() => setModalAberto(null)}
        />
      )}

      {modalAberto === 'convidado' && minha?.id && (
        <ModalConvidado
          eventoId={eventoId}
          inscricaoId={minha.id}
          onClose={() => setModalAberto(null)}
        />
      )}
    </div>
  )
}
