'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { X, Clock, MapPin, CalendarDays, Users, UserPlus, Ticket, Flame, Pencil, UserCircle } from 'lucide-react'
import { useEvento } from '@/hooks/evento/useEvento'
import { useAuthStore } from '@/store/authStore'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import {
  seloEvento,
  periodoEvento,
  hora,
  vagasRestantesCalc,
  vagasAcabando as calcVagasAcabando,
  vagasEsgotadas as calcVagasEsgotadas,
  podeEditarEvento,
} from '@/lib/formats/eventoFormat'
import { podeVerListaCompletaDeInscritos } from '@/lib/permissoes'
import { urlFoto } from '@/lib/urlFoto'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { ModalDetalheLocal } from '@/components/module/eventos/ModalDetalheLocal'
import type { EventoLocalInfo } from '@/types/evento.type'
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
  const podeGerenciar = !!evento?.podeGerenciarEsteEvento

  const { data: participantes = [] } = useParticipantes(eventoId)
  const { data: minha } = useMinhaInscricao(eventoId)
  const [modalAberto, setModalAberto] = useState<'membros' | 'convidado' | null>(null)
  const [ampliada, setAmpliada] = useState(false)
  const [localDetalhe, setLocalDetalhe] = useState<EventoLocalInfo | null>(null)

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

  const selo = evento ? seloEvento(evento) : null

  return (
    <>
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
              {selo && (
                <span className={`${styles.selo} ${styles[selo.variante]}`}>
                  {selo.label}
                </span>
              )}
              <span className={styles.dataTopo}>{periodoEvento(evento)}</span>
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
                  {/*
                    Local CADASTRADO (tem id) abre o detalhe do endereço ao clicar — é onde
                    mora o endereço herdado da igreja, que não cabe inteiro aqui. O ícone entra
                    DENTRO do botão (não como irmão), senão clicar nele não fazia nada — só o
                    texto abria o modal. O ad-hoc de texto livre não tem o que detalhar, então
                    continua sendo texto simples, com o ícone como irmão de novo.
                  */}
                  {evento.local.id ? (
                    <button
                      type="button"
                      className={styles.localBotaoLinha}
                      onClick={() => setLocalDetalhe(evento.local)}
                    >
                      <span className={styles.infoIcone}><MapPin size={20} /></span>
                      <span className={styles.localBotaoTexto}>
                        <p className={styles.infoLabel}>Local</p>
                        <p className={styles.infoValor}>{evento.local.nome}</p>
                        {evento.local.endereco && (
                          <p className={styles.infoSecundario}>
                            {evento.local.endereco}
                            {evento.local.enderecoHerdado && ' (endereço da igreja)'}
                          </p>
                        )}
                      </span>
                    </button>
                  ) : (
                  <>
                  <span className={styles.infoIcone}><MapPin size={20} /></span>
                  <div>
                    <p className={styles.infoLabel}>Local</p>
                    <p className={styles.infoValor}>{evento.local.nome}</p>
                  </div>
                  </>
                  )}
                </div>
              )}

              {evento.responsavel && (
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><UserCircle size={20} /></span>
                  <div>
                    <p className={styles.infoLabel}>Responsável</p>
                    <p className={styles.infoValor}>{evento.responsavel.nome}</p>
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
                  {esgotado && evento.situacao !== 'ENCERRADO'
                    ? 'Esgotado'
                    : `${totalPessoas} de ${vagas} vagas preenchidas`}
                </p>
                {evento.situacao !== 'ENCERRADO' && (
                  esgotado ? (
                    <p className={styles.vagasAcabando}>
                      <Flame size={14} aria-hidden="true" />
                      Esgotado
                    </p>
                  ) : mostrarVagasAcabando && (
                    <p className={styles.vagasAcabando}>
                      <Flame size={14} aria-hidden="true" />
                      {vagasRestantes === 1 ? 'Última vaga!' : `Últimas ${vagasRestantes} vagas`}
                    </p>
                  )
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

            {/* Auditoria — mesmo padrão de movimentação financeira. `id` null (pessoa/usuário
                arquivado) ainda mostra o nome congelado, então não precisa de fallback aqui. */}
            {(evento.criadoPor || evento.atualizadoPor) && (
              <p className={styles.auditoria}>
                {evento.criadoPor && <>Criado por {evento.criadoPor.nome}</>}
                {evento.criadoPor && evento.atualizadoPor && ' · '}
                {evento.atualizadoPor && <>Atualizado por {evento.atualizadoPor.nome}</>}
              </p>
            )}

            {/* Imagem */}
            <div className={styles.imagemBloco}>
              {evento.fotoId ? (
                /*
                  O banner aqui é `object-fit: cover` numa faixa de 200px — ele JÁ aparece
                  cortado. Ampliar não é só ver maior: é ver a imagem inteira.
                */
                <button
                  type="button"
                  className={styles.imagemBotao}
                  onClick={() => setAmpliada(true)}
                  aria-label={`Ampliar imagem do evento ${evento.titulo}`}
                >
                  <img src={urlFoto(evento.fotoId, 'DISPLAY')!} alt={evento.titulo} className={styles.imagem} />
                </button>
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

    {/* Irmão do overlay: dentro dele, o clique para fechar a foto fecharia o drawer junto. */}
    {ampliada && evento?.fotoId && (
      <VisualizadorFoto
        fotoId={evento.fotoId}
        descricao={`Imagem do evento ${evento.titulo}`}
        onClose={() => setAmpliada(false)}
      />
    )}

    {localDetalhe && (
      <ModalDetalheLocal local={localDetalhe} onClose={() => setLocalDetalhe(null)} />
    )}
    </>
  )
}