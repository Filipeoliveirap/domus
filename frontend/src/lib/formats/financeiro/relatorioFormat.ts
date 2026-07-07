export function formatarVariacao(valor: string): { texto: string; sinal: 'positivo' | 'negativo' | 'neutro' } {
  const num = parseFloat(valor)
  if (isNaN(num) || num === 0) {
    return { texto: '0%', sinal: 'neutro' }
  }
  const sinal = num > 0 ? 'positivo' : 'negativo'
  const prefixo = num > 0 ? '+' : ''
  const formatado = prefixo + num.toFixed(1).replace('.', ',') + '%'
  return { texto: formatado, sinal }
}

export function nomeMes(mes: number): string {
  const nomes = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']
  return nomes[mes - 1] ?? ''
}