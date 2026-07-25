'use client'

import { useRouter } from 'next/navigation'
import Link from 'next/link'
import {
  Users, Calendar, TrendingUp, Wallet, ArrowDownCircle, ArrowUpCircle, Clock, MapPin,
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useDashboard } from '@/hooks/dashboard/useDashboard'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { formatarMoeda, formatarData } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { MovimentacaoResponse } from '@/types/financeiro/movimentacao.type'
import type { EventoResumo } from '@/types/inicio.type'
import { podeVerFinanceiro } from '@/lib/permissoes'
import styles from './dashboard.module.css'

function dataEvento(iso: string) {
  const d = new Date(iso)
  return {
    dia: d.toLocaleDateString('pt-BR', { day: '2-digit' }),
    mes: d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '').toUpperCase(),
    hora: d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
  }
}

export default function DashboardPage() {
  const router = useRouter()
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)
  const { data, isLoading, isError, refetch } = useDashboard(autorizado)

  if (!hidratado) return <div className={styles.pagina} />
  if (!autorizado) return <AcessoRestrito />

  const saldoNegativo = data ? parseFloat(data.financeiro.saldoMes) < 0 : false

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Dashboard</h1>
        <p className={styles.subtitulo}>Um resumo da sua igreja.</p>
      </header>

      {isError ? (
        <EstadoErro titulo="Não foi possível carregar o dashboard" mensagem="Tente novamente." aoTentarNovamente={() => refetch()} />
      ) : (
        <>
          {/* Cards de número */}
          <div className={styles.cards}>
            <Card icone={<Users size={22} />} label="Total de pessoas"
              valor={isLoading || !data ? null : String(data.pessoas.total)}
              badge={data ? `+${data.pessoas.novosMes} este mês` : ''} badgeCor="verde" />
            <Card icone={<Calendar size={22} />} label="Eventos este mês"
              valor={isLoading || !data ? null : String(data.eventos.mes)}
              badge={data ? `${data.eventos.semana} esta semana` : ''} badgeCor="azul" />
            <Card icone={<TrendingUp size={22} />} label="Entradas do mês"
              valor={isLoading || !data ? null : formatarMoeda(data.financeiro.entradasMes)}
              badge="" badgeCor="verde" />
            <Card icone={<Wallet size={22} />} label="Saldo do mês"
              valor={isLoading || !data ? null : formatarMoeda(data.financeiro.saldoMes)}
              valorCor={saldoNegativo ? 'vermelho' : 'verde'} badge="" badgeCor="azul" />
          </div>

          {/* Listas */}
          <div className={styles.colunas}>
            {/* Movimentações recentes */}
            <section className={styles.card}>
              <div className={styles.cardHeader}>
                <div>
                  <h2 className={styles.cardTitulo}>Movimentações recentes</h2>
                  <p className={styles.cardSub}>Últimos lançamentos financeiros</p>
                </div>
                <Link href="/financeiro/movimentacoes" className={styles.verMais}>Ver todas</Link>
              </div>

              {isLoading || !data ? (
                <SkeletonLista />
              ) : data.movimentacoesRecentes.length === 0 ? (
                <p className={styles.vazio}>Nenhuma movimentação registrada.</p>
              ) : (
                <ul className={styles.listaMov}>
                  {data.movimentacoesRecentes.map((m: MovimentacaoResponse) => {
                    const entrada = m.tipo === 'ENTRADA'
                    return (
                      <li key={m.id} className={styles.itemMov}>
                        <span className={`${styles.movIcone} ${entrada ? styles.entradaBg : styles.saidaBg}`}>
                          {entrada ? <ArrowDownCircle size={16} /> : <ArrowUpCircle size={16} />}
                        </span>
                        <div className={styles.movInfo}>
                          <span className={styles.movDesc}>{m.descricao || m.categoriaNome}</span>
                          <span className={styles.movMeta}>{m.categoriaNome} · {formatarData(m.dataMovimentacao)}</span>
                        </div>
                        <span className={`${styles.movValor} ${entrada ? styles.entrada : styles.saida}`}>
                          {entrada ? '+ ' : '- '}{formatarMoeda(m.valor)}
                        </span>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>

            {/* Próximos eventos */}
            <section className={styles.card}>
              <div className={styles.cardHeader}>
                <h2 className={styles.cardTitulo}>Próximos eventos</h2>
              </div>

              {isLoading || !data ? (
                <SkeletonLista />
              ) : data.proximosEventos.length === 0 ? (
                <p className={styles.vazio}>Nenhum evento próximo.</p>
              ) : (
                <ul className={styles.listaEventos}>
                  {data.proximosEventos.map((e: EventoResumo) => {
                    const d = dataEvento(e.inicio)
                    return (
                      <li key={e.id} className={styles.itemEvento}
                        onClick={() => router.push(`/eventos?detalhe=${e.id}`)}
                        role="button" tabIndex={0}
                        onKeyDown={(ev) => ev.key === 'Enter' && router.push(`/eventos?detalhe=${e.id}`)}>
                        <div className={styles.dataChip}>
                          <span className={styles.dataMes}>{d.mes}</span>
                          <span className={styles.dataDia}>{d.dia}</span>
                        </div>
                        <div className={styles.eventoInfo}>
                          <span className={styles.eventoTitulo}>{e.titulo}</span>
                          <span className={styles.eventoMetaLinha}>
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
        </>
      )}
    </div>
  )
}

function Card({ icone, label, valor, badge, badgeCor, valorCor }: {
  icone: React.ReactNode
  label: string
  valor: string | null
  badge: string
  badgeCor: 'verde' | 'azul'
  valorCor?: 'verde' | 'vermelho'
}) {
  return (
    <div className={styles.cardNumero}>
      <div className={styles.cardTopo}>
        <span className={styles.cardIcone}>{icone}</span>
        {badge && <span className={`${styles.badge} ${badgeCor === 'verde' ? styles.badgeVerde : styles.badgeAzul}`}>{badge}</span>}
      </div>
      <span className={styles.cardLabel}>{label}</span>
      {valor === null ? (
        <Skeleton width="80px" height="28px" />
      ) : (
        <span className={`${styles.cardValor} ${valorCor === 'vermelho' ? styles.valorVermelho : ''}`}>{valor}</span>
      )}
    </div>
  )
}

function SkeletonLista() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {[0, 1, 2, 3].map((i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Skeleton width="36px" height="36px" radius="var(--radius-md)" />
          <Skeleton width="70%" height="14px" />
        </div>
      ))}
    </div>
  )
}
