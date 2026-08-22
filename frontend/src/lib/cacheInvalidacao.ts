import type { QueryClient } from '@tanstack/react-query'

type Entidade = 'evento' | 'pessoa' | 'movimentacao' | 'categoria' | 'usuario' | 'igreja' | 'inscricao' | 'localEvento' | 'ministerio' | 'visitante' | 'celula'

const AFETADAS: Record<Entidade, string[][]> = {
  evento: [
    ['eventos'],
    ['eventos-arquivados'],
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
    ['pessoas-arquivadas'],
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
    ['movimentacoes-arquivadas'],
    ['relatorios'],
    ['dashboard'],
    ['busca-global'],
  ],
  categoria: [
    ['categorias'],
    ['categorias-arquivadas'],
    ['movimentacoes'],
    ['relatorios'],
    ['dashboard'],
    ['busca-global'],
  ],
  usuario: [
    ['usuarios'],
    ['usuarios-arquivados'],
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
    // Idem pra respostas de campo personalizado: a inscrição é cancelada e reaproveitada
    // numa reinscrição (mesmo id), então sem isso a tela continuava mostrando resposta
    // velha em cache até um reload manual.
    ['respostas-campos'],
  ],
  localEvento: [
    ['eventos'],
    ['evento'],
    ['locais-evento-arquivados'],
  ],
  ministerio: [
    ['ministerios'],
    ['ministerios-arquivados'],
    ['pessoas'],
  ],
  visitante: [
    ['visitantes'],
    ['visitante'],
  ],
  celula: [
    ['celulas'],
    ['celulas-arquivadas'],
    ['visitantes'],
  ],
}

export function invalidarCache(queryClient: QueryClient, ...entidades: Entidade[]): void {
  const chaves = entidades.flatMap((e) => AFETADAS[e])
  for (const queryKey of chaves) {
    queryClient.invalidateQueries({ queryKey })
  }
}
