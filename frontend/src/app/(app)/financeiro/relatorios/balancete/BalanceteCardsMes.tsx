'use client'

import type { Balancete } from '@/types/financeiro/balancete.type'
import styles from './BalanceteCardsMes.module.css'

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro']

function formatarValor(valor: string) {
  return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function BalanceteCardsMes({ balancete }: { balancete: Balancete }) {
  return (
    <div className={styles.lista}>
      {MESES.map((nomeMes, i) => (
        <div key={nomeMes} className={styles.card}>
          <h3>{nomeMes}</h3>
          <div className={styles.linha}>
            <span>Total Entradas</span>
            <strong className={styles.entrada}>{formatarValor(balancete.subtotalEntradasPorMes[i])}</strong>
          </div>
          <div className={styles.linha}>
            <span>Total Saídas</span>
            <strong className={styles.saida}>{formatarValor(balancete.subtotalSaidasPorMes[i])}</strong>
          </div>
          <div className={styles.linha}>
            <span>Saldo do Mês</span>
            <strong>{formatarValor(balancete.saldoDoMes[i])}</strong>
          </div>
          <div className={styles.saldoAcumulado}>
            <span>Saldo Acumulado</span>
            <strong>{formatarValor(balancete.saldoAcumulado[i])}</strong>
          </div>
        </div>
      ))}
    </div>
  )
}
