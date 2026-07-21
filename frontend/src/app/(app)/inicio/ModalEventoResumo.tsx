'use client'

import { useEffect, useMemo, useState } from 'react'
import { X, CalendarDays, MapPin, Users, UserPlus, Ticket, Flame, Pencil, XCircle } from 'lucide-react'
import Link from 'next/link'
import { useAuthStore } from '@/store/authStore'
import { useRemoverConvidado } from '@/hooks/inscricao/useRemoverConvidado'
import { useEvento } from '@/hooks/evento/useEvento'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { iniciais } from '@/lib/formats/membroFormat'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { BotaoConfirmarPresenca } from '@/components/module/eventos/BotaoConfirmarPresenca'
import { ModalInscreverMembros } from '@/components/module/eventos/ModalInscreverMembros'
import { ModalConvidado } from '@/components/module/eventos/ModalConvidado'
import { ModalQuemVai } from '@/components/module/eventos/ModalQuemVai'
import {
  vagasRestantesCalc,
  vagasAcabando as calcVagasAcabando,
  vagasEsgotadas as calcVagasEsgotadas,
  podeEditarEvento,
} from '@/lib/formats/eventoFormat'
import { podeGerenciarEventos } from '@/lib/permissoes'
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
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarEventos(role)

  const [modalAberto, setModalAberto] = useState<'membros' | 'convidado' | 'lista' | null>(null)
  const [removendoId, setRemovendoId] = useState<string | null>(null)
  const removerConvidado = useRemoverConvidado()

  // Vagas contam PESSOAS: cada inscrito mais os convidados que ele trouxe. Contar só os
  // inscritos daria um "restam N" otimista, e a pessoa levaria "esgotado" na cara ao clicar.
  const totalPessoas = useMemo(
    () => participantes.reduce((acc, p) => acc + 1 + p.convidados.length, 0),
    [participantes],
  )
  const vagas = evento?.vagas ?? null
  const vagasRestantes = vagasRestantesCalc(vagas, participantes)
  const mostrarVagasAcabando = calcVagasAcabando(vagas, vagasRestantes)
  const esgotado = calcVagasEsgotadas(vagas, vagasRestantes)
  // F15: fora de AGENDADO o backend recusa qualquer nova inscrição/convidado — os botões somem.
  const inscricaoBloqueadaPelaSituacao = evento ? evento.situacao !== 'AGENDADO' : false

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
              <div className={styles.tituloLinha}>
                <h2 className={styles.titulo} id="titulo-evento">
                  {evento.titulo}
                </h2>
                {/* F13: editar direto do modal de detalhe — só quem gerencia, e só quando o backend ainda aceita edição. */}
                {podeGerenciar && podeEditarEvento(evento.situacao) && (
                  <Link href={`/eventos/${evento.id}`} className={styles.botaoEditar} aria-label="Editar evento">
                    <Pencil size={16} />
                  </Link>
                )}
              </div>

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

            {evento.preco && (
              <p className={styles.preco}>
                <Ticket size={15} aria-hidden="true" />
                <strong>{formatarMoeda(evento.preco)}</strong>
                <span className={styles.precoNota}>por pessoa</span>
              </p>
            )}

            {/*
              A pilha de avatares é clicável: ver quem vai é o que faz a pessoa querer ir.
              O vazio não vira placeholder morto — convida a ser o primeiro.
            */}
            <section className={styles.presenca}>
              {participantes.length > 0 ? (
                <button
                  type="button"
                  className={styles.presencaPessoas}
                  onClick={() => setModalAberto('lista')}
                  aria-label="Ver quem vai a este evento"
                >
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
                    {totalPessoas === 1
                      ? '1 pessoa confirmou que vai'
                      : `${totalPessoas} pessoas confirmaram que vão`}
                  </span>
                </button>
              ) : (
                <div className={styles.presencaPessoas}>
                  <span className={styles.avatarPresenca} aria-hidden="true">
                    <Users size={14} />
                  </span>
                  <span className={styles.presencaTexto}>
                    Ninguém confirmou ainda — seja o primeiro.
                  </span>
                </div>
              )}

              {/* F8: contador de ocupação — só faz sentido quando o evento tem limite de vagas. */}
              {vagas != null && (
                <p className={styles.vagasContador}>
                  {esgotado ? 'Esgotado' : `${totalPessoas} de ${vagas} vagas preenchidas`}
                </p>
              )}

              {/* F7: esgotado é um estado próprio — "últimas 0 vagas" não faz sentido. */}
              {esgotado ? (
                <p className={styles.vagasAcabando}>
                  <Flame size={14} aria-hidden="true" />
                  Esgotado
                </p>
              ) : mostrarVagasAcabando && (
                <p className={styles.vagasAcabando}>
                  <Flame size={14} aria-hidden="true" />
                  {vagasRestantes === 1
                    ? 'Última vaga!'
                    : `Últimas ${vagasRestantes} vagas`}
                </p>
              )}
            </section>

            {/* F5: remover o próprio convidado — ação leve (confirmação inline), não a crítica. */}
            {!!minha?.acompanhantes?.length && (
              <section className={styles.convidados}>
                <p className={styles.convidadosTitulo}>Seus convidados</p>
                {minha.acompanhantes.map((c) => (
                  <div key={c.id} className={styles.convidadoLinha}>
                    <span className={styles.avatarPresenca} aria-hidden="true">{iniciais(c.nome)}</span>
                    <span className={styles.convidadoNome}>{c.nome}</span>
                    {removendoId === c.id ? (
                      <span className={styles.convidadoConfirmacao}>
                        <span>Remover?</span>
                        <button
                          type="button"
                          onClick={() => setRemovendoId(null)}
                          disabled={removerConvidado.isPending}
                        >
                          Não
                        </button>
                        <button
                          type="button"
                          className={styles.convidadoConfirmar}
                          onClick={() => removerConvidado.mutate(c.id, { onSuccess: () => setRemovendoId(null) })}
                          disabled={removerConvidado.isPending}
                        >
                          {removerConvidado.isPending ? 'Removendo…' : 'Sim'}
                        </button>
                      </span>
                    ) : (
                      <button
                        type="button"
                        className={styles.convidadoRemover}
                        onClick={() => setRemovendoId(c.id)}
                        aria-label={`Remover ${c.nome}`}
                      >
                        <XCircle size={16} />
                      </button>
                    )}
                  </div>
                ))}
              </section>
            )}

            <div className={styles.acoesInscricao}>
              <BotaoConfirmarPresenca
                eventoId={eventoId}
                inicioEm={evento.inicioEm}
                vagasRestantes={vagasRestantes}
                requerInscricao={evento.requerInscricao}
                situacao={evento.situacao}
                preco={evento.preco}
              />

              {/*
                Inscrever outros e levar convidado só existem no fluxo formal: o backend
                recusa os dois quando o evento não organiza inscrição. Mostrar o botão
                aqui seria oferecer uma ação que só falha. F15: fora de AGENDADO nem aparecem.
              */}
              {evento.requerInscricao && !inscricaoBloqueadaPelaSituacao && (
                <>
                  <button
                    type="button"
                    className={styles.acaoSecundaria}
                    onClick={() => setModalAberto('membros')}
                    disabled={esgotado}
                  >
                    <Users size={16} aria-hidden="true" />
                    {esgotado ? 'Vagas esgotadas' : 'Inscrever membros'}
                  </button>

                  {!evento.exclusivoMembros && minha?.inscrito && (
                    <button
                      type="button"
                      className={styles.acaoSecundaria}
                      onClick={() => setModalAberto('convidado')}
                      disabled={esgotado}
                    >
                      <UserPlus size={16} aria-hidden="true" />
                      {esgotado ? 'Vagas esgotadas' : 'Vou levar alguém de fora'}
                    </button>
                  )}
                </>
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
          exclusivoBatizados={evento?.exclusivoBatizados ?? false}
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

      {modalAberto === 'lista' && evento && (
        <ModalQuemVai eventoId={eventoId} situacao={evento.situacao} aoFechar={() => setModalAberto(null)} />
      )}
    </div>
  )
}
