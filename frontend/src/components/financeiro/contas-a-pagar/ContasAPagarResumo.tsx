'use client'

import { AlertCircle, Clock, CheckCircle } from 'lucide-react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { ResumoResponse } from '@/types/contas-a-pagar'
import styles from './ContasAPagarResumo.module.css'

interface Props {
  data?: ResumoResponse
  carregando?: boolean
}

export function ContasAPagarResumo({ data, carregando }: Props) {
  const kpis = [
    {
      rotulo: 'Vence hoje',
      valor: data?.venceHoje ?? '0,00',
      cor: 'amarelo',
      Icone: Clock,
    },
    {
      rotulo: 'A vencer no mês',
      valor: data?.aVencerNoMes ?? '0,00',
      cor: 'azul',
      Icone: AlertCircle,
    },
    {
      rotulo: 'Em atraso',
      valor: data?.atrasadas ?? '0,00',
      cor: 'vermelho',
      Icone: AlertCircle,
    },
    {
      rotulo: 'Pagas no mês',
      valor: data?.pagasNoMes ?? '0,00',
      cor: 'verde',
      Icone: CheckCircle,
    },
  ]

  return (
    <div className={styles.grid}>
      {kpis.map(({ rotulo, valor, cor, Icone }) => (
        <div key={rotulo} className={`${styles.cartao} ${styles[cor]}`}>
          {carregando ? (
            <div className={styles.esqueleto} />
          ) : (
            <>
              <div className={styles.iconeWrapper}>
                <Icone size={18} aria-hidden="true" />
              </div>
              <div className={styles.info}>
                <span className={styles.rotulo}>{rotulo}</span>
                <span className={styles.valor}>{formatarMoeda(valor)}</span>
              </div>
            </>
          )}
        </div>
      ))}
    </div>
  )
}
