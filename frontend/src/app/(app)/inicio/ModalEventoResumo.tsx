'use client'

import { useEffect, useMemo, useState } from 'react'
import { X, CalendarDays, MapPin, Users, Share2, Ticket, Flame, Pencil, Building2, CheckCircle2 } from 'lucide-react'
import Link from 'next/link'
import Image from 'next/image'
import { useAuthStore } from '@/store/authStore'
import { useEvento } from '@/hooks/evento/useEvento'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { BotaoConfirmarPresenca } from '@/components/module/eventos/BotaoConfirmarPresenca'
import { RespostasCamposPersonalizados } from '@/components/module/eventos/RespostasCamposPersonalizados'
import { ModalInscreverAlguem } from '@/components/module/eventos/ModalInscreverAlguem'
import { ModalCompartilharConvite } from '@/components/module/eventos/ModalCompartilharConvite'
import { ModalQuemVai } from '@/components/module/eventos/ModalQuemVai'
import { ModalDetalheLocal } from '@/components/module/eventos/ModalDetalheLocal'
import { podeVerListaCompletaDeInscritos } from '@/lib/permissoes'
import type { EventoLocalInfo } from '@/types/evento.type'
import {
  vagasRestantesCalc,
  vagasAcabando as calcVagasAcabando,
  vagasEsgotadas as calcVagasEsgotadas,
  podeEditarEvento,
} from '@/lib/formats/eventoFormat'
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
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)
  const podeGerenciar = !!evento?.podeGerenciarEsteEvento
  const podeVerInscritos = podeVerListaCompletaDeInscritos(role) && podeGerenciar
  const ehOutraIgreja = !!evento && evento.igrejaOrganizadora.id !== minhaIgrejaId

  const [modalAberto, setModalAberto] = useState<'inscrever-alguem' | 'compartilhar' | 'lista' | null>(null)
  // Vira true no exato momento em que a auto-inscrição dá certo — usado só pra decidir se o
  // modal de campos personalizados abre sozinho na hora (ver RespostasCamposPersonalizados).
  const [acabouDeInscrever, setAcabouDeInscrever] = useState(false)
  const [ampliada, setAmpliada] = useState(false)
  const [localDetalhe, setLocalDetalhe] = useState<EventoLocalInfo | null>(null)

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
    <>
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

          {evento?.fotoId ? (
            /*
              Como nos drawers: a capa é `cover` numa faixa de 200px, então já aparece
              cortada. Sem `z-index` nem `position` aqui de propósito — isso criaria um
              contexto de empilhamento novo e o botão de fechar (que é filho da capa e
              conta com `z-index: 1`) sumiria atrás da imagem.
            */
            <button
              type="button"
              className={styles.capaBotao}
              onClick={() => setAmpliada(true)}
              aria-label={`Ampliar imagem do evento${evento.titulo ? ` ${evento.titulo}` : ''}`}
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos */}
              <img src={urlFoto(evento.fotoId, 'DISPLAY')!} alt="" className={styles.capaFoto} />
            </button>
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

              {ehOutraIgreja && (
                <span className={styles.igrejaOrganizadora}>
                  <Building2 size={13} aria-hidden="true" />
                  Evento compartilhado por {evento.igrejaOrganizadora.sigla ?? evento.igrejaOrganizadora.nome}
                </span>
              )}

              <div className={styles.metadados}>
                <span className={styles.chipData}>
                  <CalendarDays size={15} aria-hidden="true" />
                  {formatarQuando(evento.inicioEm)}
                </span>
                {evento.local && (
                  evento.local.id ? (
                    // Local cadastrado: clicável, abre o detalhe do endereço (inclui o herdado
                    // da igreja). Ad-hoc de texto livre não tem o que detalhar — fica estático.
                    <button
                      type="button"
                      className={`${styles.local} ${styles.localClicavel}`}
                      onClick={() => setLocalDetalhe(evento.local)}
                    >
                      <MapPin size={15} aria-hidden="true" />
                      {evento.local.nome}
                      {evento.local.enderecoHerdado && ' (endereço da igreja)'}
                    </button>
                  ) : (
                    <span className={styles.local}>
                      <MapPin size={15} aria-hidden="true" />
                      {evento.local.nome}
                    </span>
                  )
                )}
              </div>

              {minha?.inscrito && (
                <span className={styles.seloInscrito}>
                  <CheckCircle2 size={12} aria-hidden="true" />
                  {evento.situacao === 'ENCERRADO' ? 'Você participou desse evento' : 'Você está inscrito'}
                </span>
              )}
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
                        {urlFoto(p.fotoId, 'THUMB') ? (
                          <Image src={urlFoto(p.fotoId, 'THUMB')!} alt="" width={32} height={32} unoptimized className={styles.avatarPresencaFoto} />
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

            <div className={styles.acoesInscricao}>
              <BotaoConfirmarPresenca
                eventoId={eventoId}
                inicioEm={evento.inicioEm}
                vagasRestantes={vagasRestantes}
                requerInscricao={evento.requerInscricao}
                situacao={evento.situacao}
                preco={evento.preco}
                onInscritoComSucesso={() => setAcabouDeInscrever(true)}
              />

              {/* F15: fora de AGENDADO, o backend recusa — os botões nem aparecem. */}
              {evento.requerInscricao && !inscricaoBloqueadaPelaSituacao && (
                <>
                  <button
                    type="button"
                    className={styles.acaoSecundaria}
                    onClick={() => setModalAberto('inscrever-alguem')}
                    disabled={esgotado}
                  >
                    <Users size={16} aria-hidden="true" />
                    {esgotado ? 'Vagas esgotadas' : 'Inscrever alguém'}
                  </button>

                  <button
                    type="button"
                    className={styles.acaoSecundaria}
                    onClick={() => setModalAberto('compartilhar')}
                  >
                    <Share2 size={16} aria-hidden="true" />
                    Compartilhar
                  </button>
                </>
              )}
            </div>

            {evento.requerInscricao && minha?.inscrito && minha.id && (
              <RespostasCamposPersonalizados
                eventoId={evento.id}
                inscricaoId={minha.id}
                abrirAutomaticamente={acabouDeInscrever}
              />
            )}

            {podeVerInscritos && evento.requerInscricao && (
              <Link href={`/eventos/${evento.id}/inscritos`} className={styles.acaoInscritos}>
                <Users size={18} />
                Ver inscritos
              </Link>
            )}
          </div>
        )}
      </div>

      {modalAberto === 'inscrever-alguem' && (
        <ModalInscreverAlguem
          eventoId={eventoId}
          tituloEvento={evento?.titulo ?? ''}
          exclusivoMembros={evento?.exclusivoMembros ?? false}
          preco={evento?.preco}
          onClose={() => setModalAberto(null)}
        />
      )}

      {modalAberto === 'compartilhar' && (
        <ModalCompartilharConvite
          eventoId={eventoId}
          onClose={() => setModalAberto(null)}
        />
      )}

      {modalAberto === 'lista' && evento && (
        <ModalQuemVai
          eventoId={eventoId}
          situacao={evento.situacao}
          restritoPropriaIgreja={evento.restritoPropriaIgreja}
          podeGerenciarEsteEvento={podeGerenciar}
          aoFechar={() => setModalAberto(null)}
        />
      )}
    </div>

    {/* Irmão do overlay: dentro dele, o clique para fechar a foto fecharia o modal junto. */}
    {ampliada && evento?.fotoId && (
      <VisualizadorFoto
        fotoId={evento.fotoId}
        descricao={`Imagem do evento${evento.titulo ? ` ${evento.titulo}` : ''}`}
        onClose={() => setAmpliada(false)}
      />
    )}

    {localDetalhe && (
      <ModalDetalheLocal local={localDetalhe} onClose={() => setLocalDetalhe(null)} />
    )}
    </>
  )
}
