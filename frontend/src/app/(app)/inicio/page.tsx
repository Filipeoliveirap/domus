'use client'

import { Cake, Calendar, MapPin, Clock, BookOpen } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { useInicio } from '@/hooks/inicio/useInicio'
import { versiculoDoDia } from '@/lib/versiculos'
import { iniciais } from '@/lib/formats/membroFormat'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import type { Aniversariante, EventoResumo } from '@/types/inicio.type'
import styles from './inicio.module.css'

function dataEvento(iso: string): { dia: string; mes: string; hora: string } {
  const d = new Date(iso)
  return {
    dia: d.toLocaleDateString('pt-BR', { day: '2-digit' }),
    mes: d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '').toUpperCase(),
    hora: d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
  }
}

export default function InicioPage() {
  const router = useRouter()
  const nome = useAuthStore((s) => s.nome)
  const primeiroNome = nome?.trim().split(/\s+/)[0] ?? ''
  const versiculo = versiculoDoDia()
  const { data, isLoading, isError, refetch } = useInicio()

  return (
    <div className={styles.pagina}>
      {/* Saudação */}
      <header className={styles.saudacao}>
        <h1 className={styles.titulo}>Olá, {primeiroNome || 'bem-vindo'} 👋</h1>
        <p className={styles.subtitulo}>Que bom ter você por aqui hoje.</p>
      </header>

      {/* Versículo do dia */}
      <section className={styles.versiculo}>
        <div className={styles.versiculoIcone}><BookOpen size={20} /></div>
        <div>
          <span className={styles.versiculoLabel}>Versículo do dia</span>
          <p className={styles.versiculoTexto}>“{versiculo.texto}”</p>
          <span className={styles.versiculoRef}>{versiculo.ref}</span>
        </div>
      </section>

      {/* Aniversariantes + Próximos eventos */}
      <div className={styles.colunas}>
        {/* Aniversariantes do mês */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <span className={styles.cardIcone}><Cake size={18} /></span>
            <h2 className={styles.cardTitulo}>Aniversariantes do mês</h2>
          </div>

          {isLoading ? (
            <SkeletonLista />
          ) : isError ? (
            <EstadoErro titulo="Não foi possível carregar" mensagem="Tente novamente." aoTentarNovamente={() => refetch()} />
          ) : (data?.aniversariantesMes.length ?? 0) === 0 ? (
            <p className={styles.vazio}>Nenhum aniversariante este mês.</p>
          ) : (
            <ul className={styles.lista}>
              {data!.aniversariantesMes.map((a: Aniversariante) => (
                <li key={a.id} className={styles.itemAniv}>
                  <span className={styles.avatar}>{iniciais(a.nome)}</span>
                  <span className={styles.nome}>{a.nome}</span>
                  <span className={styles.diaBadge}>dia {a.dia}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Próximos eventos */}
        <section className={styles.card}>
          <div className={styles.cardHeader}>
            <span className={styles.cardIcone}><Calendar size={18} /></span>
            <h2 className={styles.cardTitulo}>Próximos eventos</h2>
          </div>

          {isLoading ? (
            <SkeletonLista />
          ) : isError ? (
            <EstadoErro titulo="Não foi possível carregar" mensagem="Tente novamente." aoTentarNovamente={() => refetch()} />
          ) : (data?.proximosEventos.length ?? 0) === 0 ? (
            <p className={styles.vazio}>Nenhum evento próximo.</p>
          ) : (
            <ul className={styles.lista}>
              {data!.proximosEventos.map((e: EventoResumo) => {
                const d = dataEvento(e.inicio)
                return (
                  <li
                    key={e.id}
                    className={`${styles.itemEvento} ${styles.clicavel}`}
                    onClick={() => router.push(`/eventos?detalhe=${e.id}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(ev) => ev.key === 'Enter' && router.push(`/eventos?detalhe=${e.id}`)}
                  >
                    <div className={styles.dataChip}>
                      <span className={styles.dataMes}>{d.mes}</span>
                      <span className={styles.dataDia}>{d.dia}</span>
                    </div>
                    <div className={styles.eventoInfo}>
                      <span className={styles.eventoTitulo}>{e.titulo}</span>
                      <span className={styles.eventoMeta}>
                        <Clock size={13} /> {d.hora}
                        {e.local && <> <MapPin size={13} /> {e.local}</>}
                      </span>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

function SkeletonLista() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {[0, 1, 2].map((i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Skeleton width="36px" height="36px" radius="var(--radius-full)" />
          <Skeleton width="60%" height="14px" />
        </div>
      ))}
    </div>
  )
}
