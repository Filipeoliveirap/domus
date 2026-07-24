'use client'

import {
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import type { PontoTendencia } from '@/types/evento.type'

interface Props {
  tendencia: PontoTendencia[]
}

/** "aaaa-mm" -> "mmm" abreviado em pt-BR, só para o eixo X (o valor completo fica no tooltip). */
function mesAbreviado(iso: string): string {
  const [ano, mes] = iso.split('-')
  const data = new Date(Number(ano), Number(mes) - 1, 1)
  return data.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')
}

/**
 * Comparecimento médio mensal dos últimos 6 meses — só eventos com `controlaPresenca=true`
 * entram na conta (Decisão 4). Mês sem dado (`comparecimentoMedio: null`) vira um GAP na
 * linha (Recharts pula pontos `null` por padrão em vez de desenhar zero), então o gráfico
 * nunca finge que "ninguém foi" num mês em que ninguém simplesmente controlou presença.
 */
export function GraficoTendenciaComparecimento({ tendencia }: Props) {
  const dados = tendencia.map((p) => ({ mes: mesAbreviado(p.mes), valor: p.comparecimentoMedio }))
  const semNenhumDado = tendencia.every((p) => p.comparecimentoMedio == null)

  if (semNenhumDado) {
    return (
      <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
        Nenhum evento do período controla presença — sem dado de tendência para mostrar.
      </p>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={dados} margin={{ top: 8, right: 16, bottom: 0, left: -16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
        <XAxis dataKey="mes" tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={false} tickLine={false} />
        <YAxis tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={false} tickLine={false} allowDecimals={false} />
        <Tooltip
          formatter={(valor: unknown) => (valor == null ? 'Sem dado' : `${valor} pessoas`)}
          contentStyle={{ fontSize: 13, borderRadius: 8 }}
        />
        <Line
          type="monotone"
          dataKey="valor"
          stroke="var(--color-primary)"
          strokeWidth={2}
          dot={{ r: 3 }}
          connectNulls={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
