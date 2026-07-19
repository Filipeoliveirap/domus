'use client'

import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { Consolidado, TotaisConsolidado } from '@/types/igreja/vinculo.type'
import styles from './congregacoes.module.css'

interface Props {
  data: Consolidado | undefined
  isLoading: boolean
  isError: boolean
  aoTentarNovamente: () => void
  aoEscolherIgreja: (igreja: { id: string; nome: string }) => void
}

function Numeros({ totais }: { totais: TotaisConsolidado }) {
  return (
    <div className={styles.numeros}>
      <div className={styles.numero}>
        <span className={styles.numeroRotulo}>Membros</span>
        <strong className={styles.numeroValor}>{totais.membros.total}</strong>
        <span className={styles.numeroDetalhe}>
          {totais.membros.ativos} ativos · {totais.membros.inativos} inativos ·{' '}
          {totais.membros.visitantes} visitantes
        </span>
      </div>

      <div className={styles.numero}>
        <span className={styles.numeroRotulo}>Eventos</span>
        <strong className={styles.numeroValor}>{totais.eventos.total}</strong>
        <span className={styles.numeroDetalhe}>
          {totais.eventos.realizados} realizados · {totais.eventos.proximos} próximos
        </span>
      </div>

      <div className={styles.numero}>
        <span className={styles.numeroRotulo}>Entradas</span>
        <strong className={`${styles.numeroValor} ${styles.positivo}`}>
          {formatarMoeda(totais.financeiro.entradas)}
        </strong>
      </div>

      <div className={styles.numero}>
        <span className={styles.numeroRotulo}>Saídas</span>
        <strong className={`${styles.numeroValor} ${styles.negativo}`}>
          {formatarMoeda(totais.financeiro.saidas)}
        </strong>
      </div>

      <div className={styles.numero}>
        <span className={styles.numeroRotulo}>Saldo</span>
        <strong className={styles.numeroValor}>{formatarMoeda(totais.financeiro.saldo)}</strong>
      </div>
    </div>
  )
}

export function VisaoGeralCongregacoes({
  data,
  isLoading,
  isError,
  aoTentarNovamente,
  aoEscolherIgreja,
}: Props) {
  if (isLoading) {
    return <div className={styles.skeleton} aria-label="Carregando consolidado" />
  }

  if (isError || !data) {
    return (
      <div className={styles.erro}>
        <p>Não foi possível carregar os números das congregações.</p>
        <button className={styles.botaoTentar} onClick={aoTentarNovamente}>
          Tentar novamente
        </button>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <section className={styles.blocoFamilia}>
        <h2 className={styles.tituloBloco}>Família (consolidado)</h2>
        <Numeros totais={data.familia} />
      </section>

      {/* A decisão de projeto que o usuário precisa enxergar: nem tudo segue o período. */}
      <p className={styles.avisoPeriodo}>
        Membros e eventos refletem a situação atual. Apenas os valores financeiros seguem o
        período selecionado.
      </p>

      <section className={styles.blocoPorIgreja}>
        <h2 className={styles.tituloBloco}>Por igreja</h2>

        {/* Desktop: tabela. Mobile: os mesmos dados viram cards (ver o CSS). */}
        <div className={styles.tabelaWrapper}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th>Igreja</th>
                <th>Membros</th>
                <th>Eventos</th>
                <th className={styles.alinhaDireita}>Saldo</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.porIgreja.map((linha) => (
                <tr key={linha.igrejaId}>
                  <td data-rotulo="Igreja">
                    <span className={styles.nomeIgreja}>{linha.nome}</span>
                    {linha.ehMae && <span className={styles.selo}>Sede</span>}
                  </td>
                  <td data-rotulo="Membros">
                    {linha.membros.total}{' '}
                    <span className={styles.detalheCelula}>
                      ({linha.membros.ativos}/{linha.membros.inativos}/{linha.membros.visitantes})
                    </span>
                  </td>
                  <td data-rotulo="Eventos">
                    {linha.eventos.total}{' '}
                    <span className={styles.detalheCelula}>
                      ({linha.eventos.realizados}/{linha.eventos.proximos})
                    </span>
                  </td>
                  <td data-rotulo="Saldo" className={styles.alinhaDireita}>
                    {formatarMoeda(linha.financeiro.saldo)}
                  </td>
                  <td className={styles.alinhaDireita}>
                    <button
                      className={styles.botaoDetalhe}
                      onClick={() => aoEscolherIgreja({ id: linha.igrejaId, nome: linha.nome })}
                    >
                      Ver relatórios
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
