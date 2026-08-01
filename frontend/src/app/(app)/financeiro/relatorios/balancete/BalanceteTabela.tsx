'use client'

import type { Balancete, LinhaCategoria } from '@/types/financeiro/balancete.type'
import styles from './BalanceteTabela.module.css'

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

function formatarValor(valor: string) {
  return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function LinhaCategoriaRow({ linha }: { linha: LinhaCategoria }) {
  const zerada = Number(linha.totalAno) === 0
  return (
    <tr className={zerada ? styles.linhaZerada : undefined}>
      <td>
        {linha.nomeCategoria}
        {linha.arquivada && <span className={styles.seloArquivada}>Arquivada</span>}
      </td>
      {linha.valoresPorMes.map((v, i) => (
        <td key={i}>{formatarValor(v)}</td>
      ))}
      <td className={styles.total}>{formatarValor(linha.totalAno)}</td>
    </tr>
  )
}

export function BalanceteTabela({ balancete }: { balancete: Balancete }) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.tabela}>
        <thead>
          <tr>
            <th>Categoria</th>
            {MESES.map((m) => (
              <th key={m}>{m}</th>
            ))}
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          <tr className={styles.secao}>
            <td colSpan={14}>Entradas</td>
          </tr>
          {balancete.entradas.map((l) => (
            <LinhaCategoriaRow key={l.categoriaId ?? l.nomeCategoria} linha={l} />
          ))}
          <tr className={styles.subtotal}>
            <td>Subtotal Entradas</td>
            {balancete.subtotalEntradasPorMes.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>

          <tr className={styles.secao}>
            <td colSpan={14}>Saídas</td>
          </tr>
          {balancete.saidas.map((l) => (
            <LinhaCategoriaRow key={l.categoriaId ?? l.nomeCategoria} linha={l} />
          ))}
          <tr className={styles.subtotal}>
            <td>Subtotal Saídas</td>
            {balancete.subtotalSaidasPorMes.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>

          <tr className={styles.saldoMes}>
            <td>Saldo do Mês</td>
            {balancete.saldoDoMes.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>
          <tr className={styles.saldoAcumulado}>
            <td>Saldo Acumulado</td>
            {balancete.saldoAcumulado.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>
        </tbody>
      </table>
    </div>
  )
}
