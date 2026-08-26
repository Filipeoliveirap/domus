'use client'

import { useEffect, useMemo, useState } from 'react'
import Link from 'next/link'
import { X, Clock, MapPin, CalendarDays, Users, Ticket, Flame, Pencil, UserCircle, Building2, Archive, Share2 } from 'lucide-react'
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
import { RespostasCamposPersonalizados } from '@/components/module/eventos/RespostasCamposPersonalizados'
import { ModalInscreverAlguem } from '@/components/module/eventos/ModalInscreverAlguem'
import { ModalCompartilharConvite } from '@/components/module/eventos/ModalCompartilharConvite'
import { ModalQuemVai } from '@/components/module/eventos/ModalQuemVai'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'

interface DrawerDetalheEventoProps {
  eventoId: string
  onClose: () => void
  /** Veio do selo de pendência clicado no card da listagem — abre o drawer já com o modal
   *  de resposta de campos personalizados aberto. */
  abrirPendenciaAoMontar?: boolean
}

const MAX_AVATARES = 3

export function DrawerDetalheEvento({ eventoId, onClose, abrirPendenciaAoMontar = false }: DrawerDetalheEventoProps) {
  const { data: evento, isPending, isError, refetch } = useEvento(eventoId)
  const role = useAuthStore((s) => s.role)
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)
  const podeGerenciar = !!evento?.podeGerenciarEsteEvento
  const podeVerInscritos = podeVerListaCompletaDeInscritos(role) && podeGerenciar
  const ehOutraIgreja = !!evento && evento.igrejaOrganizadora.id !== minhaIgrejaId

  const { data: participantes = [] } = useParticipantes(eventoId)
  const { data: minha } = useMinhaInscricao(eventoId)
  const [modalAberto, setModalAberto] = useState<'inscrever-alguem' | 'compartilhar' | 'lista' | null>(null)
  // Vira true no exato momento em que a auto-inscrição dá certo — usado só pra decidir se o
  // modal de campos personalizados abre sozinho na hora (ver RespostasCamposPersonalizados).
  const [acabouDeInscrever, setAcabouDeInscrever] = useState(false)
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
            {evento.arquivado && (
              <Link href="/eventos/arquivados" className={styles.avisoArquivado} onClick={onClose}>
                <Archive size={16} />
                <span>Este evento está arquivado. Toque para restaurá-lo na lista de arquivados.</span>
              </Link>
            )}
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
                {podeGerenciar && !evento.arquivado && podeEditarEvento(evento.situacao) && (
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
                          <img src={urlFoto(p.fotoId, 'THUMB')!} alt="" className={styles.avatarPresencaFoto} />
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

              {/* F6/F8: aviso de vagas acabando/esgotado e contador — antes só no modal do início. */}
              {vagas != null && (
                <>
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
                </>
              )}
            </section>

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
                onInscritoComSucesso={() => setAcabouDeInscrever(true)}
              />

              {/* F15: fora de AGENDADO, o backend recusa — os botões nem aparecem. */}
              {!inscricaoBloqueadaPelaSituacao && (
                <>
                  {evento.requerInscricao && (
                    <button
                      type="button"
                      className={styles.acaoSecundaria}
                      onClick={() => setModalAberto('inscrever-alguem')}
                      disabled={esgotado}
                    >
                      <Users size={16} aria-hidden="true" />
                      {esgotado ? 'Vagas esgotadas' : 'Inscrever alguém'}
                    </button>
                  )}

                  {/* Sem inscrição, o convite é só informativo (local/hora/data) — ver
                     /convite/[token]/page.tsx, que esconde o formulário quando
                     !requerInscricao. */}
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
                abrirAutomaticamente={acabouDeInscrever || abrirPendenciaAoMontar}
              />
            )}

            {podeVerInscritos && evento.requerInscricao && (
              <Link href={`/eventos/${evento.id}/inscritos`} className={styles.acaoInscritos}>
                <Users size={18} />
                Ver inscritos
              </Link>
            )}

            {modalAberto === 'inscrever-alguem' && (
              <ModalInscreverAlguem
                eventoId={evento.id}
                tituloEvento={evento.titulo}
                exclusivoMembros={evento.exclusivoMembros}
                preco={evento.preco}
                onClose={() => setModalAberto(null)}
              />
            )}

            {modalAberto === 'compartilhar' && (
              <ModalCompartilharConvite
                eventoId={evento.id}
                onClose={() => setModalAberto(null)}
              />
            )}

            {modalAberto === 'lista' && (
              <ModalQuemVai
                eventoId={evento.id}
                situacao={evento.situacao}
                restritoPropriaIgreja={evento.restritoPropriaIgreja}
                podeGerenciarEsteEvento={podeGerenciar}
                aoFechar={() => setModalAberto(null)}
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