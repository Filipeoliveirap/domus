'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import Image from 'next/image'
import { Cake, Calendar, MapPin, Clock, Quote, ArrowRight, PartyPopper, X, Building2, CheckCircle2 } from 'lucide-react'
import { clsx } from 'clsx'
import { useAuthStore } from '@/store/authStore'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { CarrosselSuave, type CarrosselSuaveRef } from '@/components/common/CarrosselSuave/CarrosselSuave'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { ModalPerfilResumo, type PerfilResumoDados, type PosicaoTarget } from '@/components/common/ModalPerfilResumo/ModalPerfilResumo'
import { useInicio } from '@/hooks/inicio/useInicio'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { versiculoDoDia } from '@/lib/versiculos'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { ModalEventoResumo } from './ModalEventoResumo'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { MuralAvisosCarrossel } from './MuralAvisosCarrossel'
import { FeedComunidade } from './FeedComunidade'
import { ChipAtalhosMobile } from './ChipAtalhosMobile'
import type { Aniversariante, EventoResumo } from '@/types/inicio.type'
import styles from './inicio.module.css'

const ANIVERSARIANTES_NO_CARD = 4

function dataEvento(iso: string): { dia: string; mes: string; hora: string } {
  const d = new Date(iso)
  return {
    dia: d.toLocaleDateString('pt-BR', { day: '2-digit' }),
    mes: d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '').toUpperCase(),
    hora: d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
  }
}

function Avatar({ nome, fotoId, onVerFoto }: { nome: string; fotoId: string | null; onVerFoto?: () => void }) {
  const url = urlFoto(fotoId, 'THUMB')
  if (url && onVerFoto) {
    return (
      <button
        type="button"
        className={styles.avatar}
        onClick={(e) => { e.stopPropagation(); onVerFoto() }}
        aria-label={`Ver foto de ${nome}`}
      >
        <Image src={url} alt="" width={40} height={40} unoptimized className={styles.avatarFoto} />
      </button>
    )
  }
  return (
    <span className={styles.avatar}>
      {url ? (
        <Image src={url} alt="" width={40} height={40} unoptimized className={styles.avatarFoto} />
      ) : (
        iniciais(nome)
      )}
    </span>
  )
}

function SeloInscritoCard({ eventoId }: { eventoId: string }) {
  const { data: minha } = useMinhaInscricao(eventoId)
  if (!minha?.inscrito) return null
  return (
    <span className={styles.eventoInscrito}>
      <CheckCircle2 size={11} aria-hidden="true" />
      {minha.requerInscricao ? 'Você está inscrito' : 'Você vai'}
    </span>
  )
}

function ItemAniversariante({
  aniversariante: a,
  hoje,
  onVerFoto,
  onAbrirPessoa,
  onAbrirPerfil,
}: {
  aniversariante: Aniversariante
  hoje: number
  onVerFoto: (a: Aniversariante) => void
  onAbrirPessoa: (id: string) => void
  onAbrirPerfil: (e: React.MouseEvent, a: Aniversariante) => void
}) {
  const ehHoje = a.dia === hoje
  const primeiroNome = doisPrimeirosNomes(a.nome)
  const linkParabens = ehHoje && a.telefone
    ? `https://wa.me/55${a.telefone.replace(/\D/g, '')}?text=${encodeURIComponent(`Feliz aniversário, ${primeiroNome}! 🎉`)}`
    : null

  return (
    <li
      className={`${styles.itemAniv} ${ehHoje ? styles.anivHoje : ''} ${styles.itemAnivClicavel}`}
      onClick={(e) => onAbrirPerfil(e, a)}
      onKeyDown={(e) => { if (e.key === 'Enter') onAbrirPerfil(a) }}
      role="button"
      tabIndex={0}
    >
      <Avatar nome={a.nome} fotoId={a.fotoId} onVerFoto={a.fotoId ? () => onVerFoto(a) : undefined} />
      <span className={styles.anivInfo}>
        <span className={styles.anivNome}>{primeiroNome}</span>
        <span className={styles.anivData}>{ehHoje ? 'Hoje' : `Dia ${a.dia}`}</span>
      </span>
      {linkParabens ? (
        <a
          href={linkParabens}
          target="_blank"
          rel="noopener noreferrer"
          className={styles.parabens}
          onClick={(e) => e.stopPropagation()}
        >
          <PartyPopper size={14} aria-hidden="true" />
          Parabéns
        </a>
      ) : ehHoje ? (
        <PartyPopper size={18} className={styles.iconeHoje} aria-label="Aniversário hoje" />
      ) : null}
    </li>
  )
}

function ModalAniversariantes({
  aniversariantes,
  hoje,
  aoFechar,
  onVerFoto,
  onAbrirPessoa,
  onAbrirPerfil,
}: {
  aniversariantes: Aniversariante[]
  hoje: number
  aoFechar: () => void
  onVerFoto: (a: Aniversariante) => void
  onAbrirPessoa: (id: string) => void
  onAbrirPerfil: (a: Aniversariante) => void
}) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = ''
    }
  }, [])

  const handleAbrirPessoa = (id: string) => {
    fechar()
    onAbrirPessoa(id)
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-aniversariantes"
      >
        <span className={styles.grabber} aria-hidden="true" />
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo} id="titulo-aniversariantes">
            Aniversariantes do mês ({aniversariantes.length})
          </h2>
          <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>
        <div className={styles.modalCorpo}>
          <ul className={styles.listaAniv}>
            {aniversariantes.map((a) => (
              <ItemAniversariante
                key={a.id}
                aniversariante={a}
                hoje={hoje}
                onVerFoto={onVerFoto}
                onAbrirPessoa={handleAbrirPessoa}
                onAbrirPerfil={onAbrirPerfil}
              />
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}

function SkeletonLista() {
  return (
    <div className={styles.trilhaEventos}>
      <Skeleton style={{ height: 140, borderRadius: 16 }} />
      <Skeleton style={{ height: 140, borderRadius: 16 }} />
    </div>
  )
}

export default function InicioPage() {
  const router = useRouter()
  const carrosselEventosRef = useRef<CarrosselSuaveRef>(null)
  const nome = useAuthStore((s) => s.nome)
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)
  const primeiroNome = doisPrimeirosNomes(nome ?? '')
  const versiculo = versiculoDoDia()
  const { data, isLoading, isError, refetch } = useInicio()
  const [modalAberto, setModalAberto] = useState(false)
  const [eventoAberto, setEventoAberto] = useState<string | null>(null)
  const [fotoAniv, setFotoAniv] = useState<Aniversariante | null>(null)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)
  const [perfilResumo, setPerfilResumo] = useState<PerfilResumoDados | null>(null)
  const [posicaoTarget, setPosicaoTarget] = useState<PosicaoTarget | null>(null)

  const handleAbrirPerfil = (e: React.MouseEvent, a: Aniversariante) => {
    const rect = e.currentTarget.getBoundingClientRect()
    setPosicaoTarget({
      top: rect.top,
      left: rect.left,
      bottom: rect.bottom,
      right: rect.right,
      width: rect.width,
      height: rect.height,
    })
    setPerfilResumo({ id: a.id, nome: a.nome, fotoId: a.fotoId })
  }
  const hoje = new Date().getDate()
  const eventos = data?.proximosEventos ?? []

  const aniversariantes = useMemo(() => {
    const faixa = (dia: number) => (dia === hoje ? 0 : dia > hoje ? 1 : 2)
    return [...(data?.aniversariantesMes ?? [])].sort(
      (a, b) => faixa(a.dia) - faixa(b.dia) || a.dia - b.dia,
    )
  }, [data?.aniversariantesMes, hoje])

  return (
    <div className={styles.pagina}>
      <section className={styles.hero}>
        <div className={styles.heroImagem} aria-hidden="true" />
        <div className={styles.heroVeu} aria-hidden="true" />
        <div className={styles.heroConteudo}>
          <h1 className={styles.heroTitulo}>
            Bem-vindo à comunidade,
            <br />
            <span className={styles.heroNome}>{primeiroNome || 'bem-vindo'}!</span>
          </h1>
          <p className={styles.heroSubtitulo}>Veja o que está acontecendo na sua igreja hoje.</p>
        </div>
      </section>

      {/* Carrossel do Mural Oficial de Avisos */}
      <MuralAvisosCarrossel />

      {/* Atalhos rápidos para Mobile */}
      <ChipAtalhosMobile
        totalAniversariantes={aniversariantes.length}
        onAbrirAniversariantes={() => setModalAberto(true)}
      />

      <div className={styles.colunas}>
        {/* Coluna Principal: Feed da Comunidade (Estilo X / Microblog) */}
        <div className={styles.colunaPrincipal}>
          <FeedComunidade />
        </div>

        {/* Coluna Lateral: Versículo, Próximos Eventos e Aniversariantes */}
        <aside className={styles.colunaLateral}>
          <section className={styles.versiculo}>
            <Quote size={96} className={styles.versiculoAspas} aria-hidden="true" />
            <span className={styles.versiculoLabel}>Versículo do dia</span>
            <p className={styles.versiculoTexto}>&ldquo;{versiculo.texto}&rdquo;</p>
            <span className={styles.versiculoRef}>— {versiculo.ref}</span>
          </section>

          <section>
            <div className={styles.secaoHeader}>
              <h2 className={styles.secaoTitulo}>Próximos eventos</h2>
              <button className={styles.verTodos} onClick={() => router.push('/eventos')}>
                Ver todos <ArrowRight size={14} aria-hidden="true" />
              </button>
            </div>
            <Transicao key={isLoading ? 'load' : isError ? 'erro' : eventos.length ? 'cheio' : 'vazio'} modo="fade">
              {isLoading ? (
                <SkeletonLista />
              ) : isError ? (
                <EstadoErro
                  titulo="Não foi possível carregar"
                  mensagem="Tente novamente."
                  aoTentarNovamente={() => refetch()}
                />
              ) : eventos.length === 0 ? (
                <EstadoVazio
                  icone={Calendar}
                  titulo="Nenhum evento próximo"
                  mensagem="Quando a igreja marcar algo, aparece aqui."
                  acaoPrimaria={{ label: 'Ver eventos', onClick: () => router.push('/eventos') }}
                />
              ) : (
                <CarrosselSuave ref={carrosselEventosRef} className={styles.trilhaEventos}>
                  {eventos.map((e: EventoResumo) => {
                    const d = dataEvento(e.inicio)
                    const ehOutraIgreja = e.igrejaOrganizadora.id !== minhaIgrejaId
                    return (
                      <button
                        key={e.id}
                        className={`${styles.cardEvento} card-interativo`}
                        onClick={() => setEventoAberto(e.id)}
                      >
                        <div>
                          <div className={styles.cardEventoTopo}>
                            <span className={styles.dataChip}>
                              <span className={styles.dataMes}>{d.mes}</span>
                              <span className={styles.dataDia}>{d.dia}</span>
                            </span>
                            <Calendar size={18} className={styles.iconeEvento} aria-hidden="true" />
                          </div>
                          <span className={styles.eventoTitulo}>{e.titulo}</span>
                          <span className={styles.eventoMeta}>
                            <Clock size={13} aria-hidden="true" /> {d.hora}
                            {e.local && (
                              <>
                                <MapPin size={13} aria-hidden="true" /> {e.local}
                              </>
                            )}
                          </span>
                          {ehOutraIgreja && (
                            <span className={styles.eventoIgreja}>
                              <Building2 size={13} aria-hidden="true" />
                              Compartilhado por {e.igrejaOrganizadora.sigla ?? e.igrejaOrganizadora.nome}
                            </span>
                          )}
                          <SeloInscritoCard eventoId={e.id} />
                        </div>
                        <span className={`${styles.eventoAcao} card-cta`}>
                          Ver detalhes
                          <ArrowRight size={13} className="card-seta" aria-hidden="true" />
                        </span>
                      </button>
                    )
                  })}
                </CarrosselSuave>
              )}
            </Transicao>
          </section>

          <section>
            <div className={styles.secaoHeader}>
              <div className={styles.tituloComIcone}>
                <Cake size={18} className={styles.iconeSecao} aria-hidden="true" />
                <h2 className={styles.secaoTitulo}>Aniversariantes do mês</h2>
              </div>
              {aniversariantes.length > ANIVERSARIANTES_NO_CARD && (
                <button
                  type="button"
                  className={styles.verTodos}
                  onClick={() => setModalAberto(true)}
                >
                  Ver todos ({aniversariantes.length})
                </button>
              )}
            </div>
            {isLoading ? (
              <Skeleton style={{ height: 120, borderRadius: 16 }} />
            ) : isError ? (
              <EstadoErro
                titulo="Não foi possível carregar"
                mensagem="Tente novamente."
                aoTentarNovamente={() => refetch()}
              />
            ) : aniversariantes.length === 0 ? (
              <div className={`${styles.cardAnivVazio} card-painel`}>
                <p className={styles.anivVazioTexto}>
                  Nenhum aniversariante cadastrado neste mês.
                </p>
              </div>
            ) : (
              <div className={`${styles.cardAniv} card-painel`}>
                <ul className={styles.listaAniv}>
                  {aniversariantes.slice(0, ANIVERSARIANTES_NO_CARD).map((a) => (
                    <ItemAniversariante
                      key={a.id}
                      aniversariante={a}
                      hoje={hoje}
                      onVerFoto={setFotoAniv}
                      onAbrirPessoa={setPessoaDetalheId}
                      onAbrirPerfil={handleAbrirPerfil}
                    />
                  ))}
                </ul>
              </div>
            )}
          </section>
        </aside>
      </div>

      {modalAberto && (
        <ModalAniversariantes
          aniversariantes={aniversariantes}
          hoje={hoje}
          aoFechar={() => setModalAberto(false)}
          onVerFoto={setFotoAniv}
          onAbrirPessoa={setPessoaDetalheId}
          onAbrirPerfil={handleAbrirPerfil}
        />
      )}

      {eventoAberto && (
        <ModalEventoResumo
          eventoId={eventoAberto}
          aoFechar={() => setEventoAberto(null)}
        />
      )}

      {fotoAniv && fotoAniv.fotoId && (
        <VisualizadorFoto
          fotoId={fotoAniv.fotoId}
          descricao={fotoAniv.nome}
          onClose={() => setFotoAniv(null)}
        />
      )}

      {pessoaDetalheId && (
        <DrawerDetalhePessoa
          pessoaId={pessoaDetalheId}
          onClose={() => setPessoaDetalheId(null)}
        />
      )}

      {perfilResumo && (
        <ModalPerfilResumo
          dados={perfilResumo}
          posicaoTarget={posicaoTarget}
          aoFechar={() => {
            setPerfilResumo(null)
            setPosicaoTarget(null)
          }}
          onVerDetalhesCompletos={(id) => setPessoaDetalheId(id)}
        />
      )}
    </div>
  )
}
