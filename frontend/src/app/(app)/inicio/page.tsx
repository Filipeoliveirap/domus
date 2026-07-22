'use client'

import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Cake, Calendar, MapPin, Clock, Quote, ArrowRight, PartyPopper, X } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useInicio } from '@/hooks/inicio/useInicio'
import { versiculoDoDia } from '@/lib/versiculos'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { ModalEventoResumo } from './ModalEventoResumo'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import type { Aniversariante, EventoResumo } from '@/types/inicio.type'
import styles from './inicio.module.css'

/** Quantos aniversariantes cabem no card antes de valer a pena abrir o modal. */
const ANIVERSARIANTES_NO_CARD = 4

function dataEvento(iso: string): { dia: string; mes: string; hora: string } {
  const d = new Date(iso)
  return {
    dia: d.toLocaleDateString('pt-BR', { day: '2-digit' }),
    mes: d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '').toUpperCase(),
    hora: d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
  }
}

/** Foto quando existe, iniciais quando não — o upload de foto é da Fase 2. */
function Avatar({ nome, foto }: { nome: string; foto: string | null }) {
  return (
    <span className={styles.avatar}>
      {foto ? (
        // eslint-disable-next-line @next/next/no-img-element -- URL de storage externo; next/image exigiria configurar domínios
        <img src={foto} alt="" className={styles.avatarFoto} />
      ) : (
        iniciais(nome)
      )}
    </span>
  )
}

function ItemAniversariante({
  aniversariante,
  hoje,
}: {
  aniversariante: Aniversariante
  hoje: number
}) {
  const ehHoje = aniversariante.dia === hoje

  return (
    <li className={`${styles.itemAniv} ${ehHoje ? styles.anivHoje : ''}`}>
      <Avatar nome={aniversariante.nome} foto={aniversariante.foto} />
      <span className={styles.anivInfo}>
        <span className={styles.anivNome}>{aniversariante.nome}</span>
        <span className={styles.anivData}>{ehHoje ? 'Hoje' : `Dia ${aniversariante.dia}`}</span>
      </span>
      {ehHoje && <PartyPopper size={18} className={styles.iconeHoje} aria-label="Aniversário hoje" />}
    </li>
  )
}

function ModalAniversariantes({
  aniversariantes,
  hoje,
  aoFechar,
}: {
  aniversariantes: Aniversariante[]
  hoje: number
  aoFechar: () => void
}) {
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
        aria-labelledby="titulo-aniversariantes"
      >
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo} id="titulo-aniversariantes">
            Aniversariantes do mês ({aniversariantes.length})
          </h2>
          <button type="button" className={styles.modalFechar} onClick={aoFechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        {/* A rolagem fica no corpo, não no modal: assim o cabeçalho continua visível. */}
        <div className={styles.modalCorpo}>
          <ul className={styles.listaAniv}>
            {aniversariantes.map((a) => (
              <ItemAniversariante key={a.id} aniversariante={a} hoje={hoje} />
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}

export default function InicioPage() {
  const router = useRouter()
  const nome = useAuthStore((s) => s.nome)
  const primeiroNome = nome?.trim().split(/\s+/)[0] ?? ''
  const versiculo = versiculoDoDia()
  const { data, isLoading, isError, refetch } = useInicio()
  const [modalAberto, setModalAberto] = useState(false)
  // Detalhe do evento abre AQUI mesmo, sem sair do início.
  const [eventoAberto, setEventoAberto] = useState<string | null>(null)

  const hoje = new Date().getDate()
  const eventos = data?.proximosEventos ?? []

  /*
   * O backend devolve por dia do mês (1, 2, 3...). Aqui reordenamos para o que interessa
   * a quem abre a tela: HOJE primeiro, depois quem ainda vem, e só então quem já passou.
   *
   * A terceira faixa importa por causa do card, que mostra apenas os 4 primeiros: sem ela,
   * um aniversariante de hoje viria seguido dos dias 1, 2 e 3 — três datas já passadas —
   * escondendo no modal justamente as pessoas que ainda dá tempo de parabenizar.
   */
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

      <div className={styles.colunas}>
        <div className={styles.colunaPrincipal}>
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

            {isLoading ? (
              <SkeletonLista />
            ) : isError ? (
              <EstadoErro
                titulo="Não foi possível carregar"
                mensagem="Tente novamente."
                aoTentarNovamente={() => refetch()}
              />
            ) : eventos.length === 0 ? (
              <p className={styles.vazio}>Nenhum evento próximo por enquanto.</p>
            ) : (
              <div className={styles.trilhaEventos}>
                {eventos.map((e: EventoResumo) => {
                  const d = dataEvento(e.inicio)
                  return (
                    <button
                      key={e.id}
                      className={styles.cardEvento}
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
                      </div>
                      <span className={styles.eventoAcao}>Ver detalhes</span>
                    </button>
                  )
                })}
              </div>
            )}
          </section>
        </div>

        <div className={styles.colunaLateral}>
          <section className={styles.cardLateral}>
            <div className={styles.cardLateralHeader}>
              <span className={styles.cardLateralIcone}>
                <Cake size={16} aria-hidden="true" />
              </span>
              <h2 className={styles.cardLateralTitulo}>Aniversariantes do mês</h2>
            </div>

            {isLoading ? (
              <SkeletonLista />
            ) : isError ? (
              <EstadoErro
                titulo="Não foi possível carregar"
                mensagem="Tente novamente."
                aoTentarNovamente={() => refetch()}
              />
            ) : aniversariantes.length === 0 ? (
              <p className={styles.vazio}>Nenhum aniversariante este mês.</p>
            ) : (
              <>
                <ul className={styles.listaAniv}>
                  {aniversariantes.slice(0, ANIVERSARIANTES_NO_CARD).map((a) => (
                    <ItemAniversariante key={a.id} aniversariante={a} hoje={hoje} />
                  ))}
                </ul>

                {/* Só oferece o modal quando há mais gente do que cabe no card. */}
                {aniversariantes.length > ANIVERSARIANTES_NO_CARD && (
                  <button className={styles.botaoVerTodos} onClick={() => setModalAberto(true)}>
                    Ver todos os {aniversariantes.length} aniversariantes
                  </button>
                )}
              </>
            )}
          </section>
        </div>
      </div>

      {eventoAberto && (
        <ModalEventoResumo eventoId={eventoAberto} aoFechar={() => setEventoAberto(null)} />
      )}

      {modalAberto && (
        <ModalAniversariantes
          aniversariantes={aniversariantes}
          hoje={hoje}
          aoFechar={() => setModalAberto(false)}
        />
      )}
    </div>
  )
}

function SkeletonLista() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {[0, 1, 2].map((i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Skeleton width="40px" height="40px" radius="var(--radius-full)" />
          <Skeleton width="60%" height="14px" />
        </div>
      ))}
    </div>
  )
}
