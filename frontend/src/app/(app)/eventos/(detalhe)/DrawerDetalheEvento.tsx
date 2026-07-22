'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { X, Clock, MapPin, CalendarDays, Users, UserPlus, Ticket, Flame, Pencil } from 'lucide-react'
import { useEvento } from '@/hooks/evento/useEvento'
import { useAuthStore } from '@/store/authStore'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import {
  statusEvento,
  rotuloStatus,
  varianteStatus,
  dataExtenso,
  hora,
  vagasRestantesCalc,
  vagasAcabando as calcVagasAcabando,
  vagasEsgotadas as calcVagasEsgotadas,
  podeEditarEvento,
} from '@/lib/formats/eventoFormat'
import { podeVerListaCompletaDeInscritos, podeGerenciarEventos } from '@/lib/permissoes'
import styles from './DrawerDetalheEvento.module.css'
import { SkeletonDrawerEvento } from "./SkeletonDrawerEvento";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { BotaoConfirmarPresenca } from '@/components/module/eventos/BotaoConfirmarPresenca'
import { ModalInscreverPessoas } from '@/components/module/eventos/ModalInscreverPessoas'
import { ModalConvidado } from '@/components/module/eventos/ModalConvidado'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'

interface DrawerDetalheEventoProps {
  eventoId: string
  onClose: () => void
}

export function DrawerDetalheEvento({ eventoId, onClose }: DrawerDetalheEventoProps) {
  const { data: evento, isPending, isError, refetch } = useEvento(eventoId)
  const role = useAuthStore((s) => s.role)
  const podeVerInscritos = podeVerListaCompletaDeInscritos(role)
  // A6/rodada 3: mesma regra do modal do início — só quem gerencia, e só enquanto editável.
  const podeGerenciar = podeGerenciarEventos(role)

  const { data: participantes = [] } = useParticipantes(eventoId)
  const { data: minha } = useMinhaInscricao(eventoId)
  const [modalAberto, setModalAberto] = useState<'membros' | 'convidado' | null>(null)

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
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const status = evento ? statusEvento(evento) : null

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <aside
        className={styles.drawer}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        {isPending ? (
          <SkeletonDrawerEvento />
        ) : isError || !evento ? (
          <EstadoErro
            titulo="Não foi possível carregar o evento"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <div className={styles.conteudo}>
            {/* Cabeçalho */}
            <header className={styles.header}>
              {status && (
                <span className={`${styles.selo} ${styles[varianteStatus(status)]}`}>
                  {rotuloStatus(status)}
                </span>
              )}
              <span className={styles.dataTopo}>{dataExtenso(evento.inicioEm)}</span>
              <div className={styles.tituloLinha}>
                <h2 className={styles.titulo}>{evento.titulo}</h2>
                {/* A6/rodada 3: mesma affordance do modal do início — só quem gerencia, e só enquanto editável. */}
                {podeGerenciar && podeEditarEvento(evento.situacao) && (
                  <Link href={`/eventos/${evento.id}`} className={styles.botaoEditar} aria-label="Editar evento">
                    <Pencil size={16} />
                  </Link>
                )}
              </div>
            </header>

            {/* Infos */}
            <div className={styles.infos}>
              <div className={styles.infoItem}>
                <span className={styles.infoIcone}><Clock size={20} /></span>
                <div>
                  <p className={styles.infoLabel}>Horário</p>
                  <p className={styles.infoValor}>
                    {hora(evento.inicioEm)}
                    {evento.fimEm && ` — ${hora(evento.fimEm)}`}
                  </p>
                </div>
              </div>

              {evento.local && (
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><MapPin size={20} /></span>
                  <div>
                    <p className={styles.infoLabel}>Local</p>
                    <p className={styles.infoValor}>{evento.local}</p>
                  </div>
                </div>
              )}

              {/* F1: preço só aparecia no modal do início — agora aparece aqui também. */}
              {evento.preco && (
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><Ticket size={20} /></span>
                  <div>
                    <p className={styles.infoLabel}>Preço</p>
                    <p className={styles.infoValor}>{formatarMoeda(evento.preco)} por pessoa</p>
                  </div>
                </div>
              )}
            </div>

            {/* F6/F8: aviso de vagas acabando/esgotado e contador — antes só no modal do início. */}
            {vagas != null && (
              <div className={styles.vagasBloco}>
                <p className={styles.vagasContador}>
                  {esgotado ? 'Esgotado' : `${totalPessoas} de ${vagas} vagas preenchidas`}
                </p>
                {esgotado ? (
                  <p className={styles.vagasAcabando}>
                    <Flame size={14} aria-hidden="true" />
                    Esgotado
                  </p>
                ) : mostrarVagasAcabando && (
                  <p className={styles.vagasAcabando}>
                    <Flame size={14} aria-hidden="true" />
                    {vagasRestantes === 1 ? 'Última vaga!' : `Últimas ${vagasRestantes} vagas`}
                  </p>
                )}
              </div>
            )}

            {/* Descrição */}
            {evento.descricao && (
              <div className={styles.descricaoBloco}>
                <p className={styles.infoLabel}>Descrição</p>
                <p className={styles.descricaoTexto}>{evento.descricao}</p>
              </div>
            )}

            {/* Imagem */}
            <div className={styles.imagemBloco}>
              {evento.foto ? (
                <img src={evento.foto} alt={evento.titulo} className={styles.imagem} />
              ) : (
                <div className={styles.imagemPlaceholder}>
                  <CalendarDays size={40} />
                </div>
              )}
            </div>

            {/*
              As mesmas ações do modal do início: quem abre o evento por aqui não deveria
              precisar sair da tela para confirmar presença.
            */}
            <div className={styles.acoesInscricao}>
              <BotaoConfirmarPresenca
                eventoId={evento.id}
                inicioEm={evento.inicioEm}
                vagasRestantes={vagasRestantes}
                requerInscricao={evento.requerInscricao}
                situacao={evento.situacao}
                preco={evento.preco}
              />

              {/* F15: fora de AGENDADO, o backend recusa — os botões nem aparecem. */}
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

            {podeVerInscritos && evento.requerInscricao && (
              <Link href={`/eventos/${evento.id}/inscritos`} className={styles.acaoInscritos}>
                <Users size={18} />
                Ver inscritos
              </Link>
            )}

            {modalAberto === 'membros' && (
              <ModalInscreverPessoas
                eventoId={evento.id}
                tituloEvento={evento.titulo}
                exclusivoMembros={evento.exclusivoMembros}
                onClose={() => setModalAberto(null)}
              />
            )}

            {modalAberto === 'convidado' && minha?.id && (
              <ModalConvidado
                eventoId={evento.id}
                inscricaoId={minha.id}
                onClose={() => setModalAberto(null)}
              />
            )}
          </div>
        )}
      </aside>
    </div>
  )
}