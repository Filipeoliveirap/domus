'use client'

import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { nomeMes } from '@/lib/formats/financeiro/relatorioFormat'
import type { EvolucaoMensal } from '@/types/financeiro/relatorio.type'
import styles from './GraficoEvolucao.module.css'

interface GraficoEvolucaoProps {
  data: EvolucaoMensal[] | undefined
  isLoading: boolean
  isError: boolean
}

export function GraficoEvolucao({ data, isLoading, isError }: GraficoEvolucaoProps) {
  if (isLoading) {
    return <div className={styles.skeleton} />
  }

  if (isError || !data) {
    return <div className={styles.erro}>Não foi possível carregar a evolução mensal.</div>
  }

  if (data.length === 0) {
    return (
      <div className={styles.painel}>
        <h3 className={styles.titulo}>Evolução Mensal</h3>
        <div className={styles.vazio}>Nenhuma movimentação no período selecionado.</div>
      </div>
    )
  }

  const dadosGrafico = data.map((m) => ({
    mes: `${nomeMes(m.mes)}/${String(m.ano).slice(2)}`,
    Entradas: parseFloat(m.entradas),
    Saídas: parseFloat(m.saidas),
  }))

  return (
    <div className={styles.painel}>
      <h3 className={styles.titulo}>Evolução Mensal</h3>
      <div className={styles.grafico}>
        <ResponsiveContainer width="100%" height={320}>
          <BarChart data={dadosGrafico} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-subtle)" vertical={false} />
            <XAxis
              dataKey="mes"
              tick={{ fill: 'var(--color-text-muted)', fontSize: 12 }}
              axisLine={{ stroke: 'var(--color-border)' }}
              tickLine={false}
            />
            <YAxis
              tick={{ fill: 'var(--color-text-muted)', fontSize: 12 }}
              axisLine={false}
              tickLine={false}
              tickFormatter={(v) => `R$ ${(v / 1000).toFixed(0)}k`}
            />
            <Tooltip
              formatter={(value) => formatarMoeda(String(value))}
              contentStyle={{
                borderRadius: '8px',
                border: '1px solid var(--color-border)',
                fontSize: '13px',
              }}
              cursor={{ fill: 'rgba(37, 99, 235, 0.05)' }}
            />
            <Legend
              wrapperStyle={{ fontSize: '13px', paddingTop: '12px' }}
              iconType="circle"
            />
            <Bar dataKey="Entradas" fill="#15803D" radius={[4, 4, 0, 0]} maxBarSize={40} />
            <Bar dataKey="Saídas" fill="#B91C1C" radius={[4, 4, 0, 0]} maxBarSize={40} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}