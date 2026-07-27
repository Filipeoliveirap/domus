import type { QueryClient } from '@tanstack/react-query'

type Entidade = 'evento' | 'pessoa' | 'movimentacao' | 'categoria' | 'usuario' | 'igreja' | 'inscricao' | 'localEvento' | 'ministerio' | 'visitante' | 'celula'

const AFETADAS: Record<Entidade, string[][]> = {
  evento: [
    ['eventos'],
    // `['eventos']` não cobre `['evento', id]` (detalhe) — o TanStack invalida por prefixo.
    ['evento'],
    ['inicio'],
    ['dashboard'],
    ['busca-global'],
    // Atualizar evento cancela inscrições (ex.: ao ativar exclusivoMembros).
    ['inscricoes'],
  ],
  pessoa: [
    ['pessoas'],
    // `['pessoas']` não cobre `['pessoa', id]` (detalhe).
    ['pessoa'],
    ['usuarios'],
    ['inicio'],
    ['dashboard'],
    ['busca-global'],
    ['relatorios'],
  ],
  movimentacao: [
    ['movimentacoes'],
    ['relatorios'],
    ['dashboard'],
    ['busca-global'],
  ],
  categoria: [
    ['categorias'],
    ['movimentacoes'],
    ['relatorios'],
    ['dashboard'],
    ['busca-global'],
  ],
  usuario: [
    ['usuarios'],
    ['busca-global'],
  ],
  igreja: [
    ['igreja'],
    ['igrejas-vinculadas'],
    ['relatorios'],
  ],
  inscricao: [
    ['inscricoes'],
    ['eventos'],
    ['evento'],
    ['inicio'],
    // `['elegibilidade', eventoId]` não é prefixo de `['inscricoes']` — sem esta
    // linha o botão de inscrição mostrava impedimento velho após cancelar/inscrever.
    ['elegibilidade'],
  ],
  localEvento: [
    ['eventos'],
    ['evento'],
  ],
  ministerio: [
    ['ministerios'],
    ['pessoas'],
  ],
  visitante: [
    ['visitantes'],
    ['visitante'],
  ],
  celula: [
    ['celulas'],
    ['visitantes'],
  ],
}

export function invalidarCache(queryClient: QueryClient, ...entidades: Entidade[]): void {
  const chaves = entidades.flatMap((e) => AFETADAS[e])
  for (const queryKey of chaves) {
    queryClient.invalidateQueries({ queryKey })
  }
}
