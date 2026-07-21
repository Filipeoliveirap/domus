import type { QueryClient } from '@tanstack/react-query'

/**
 * Mapa único de "o que fica velho quando isto muda".
 *
 * <p>Existe por causa de um bug real (2026-07-19): cadastrar um evento não atualizava a tela
 * de início — o evento só aparecia depois de recarregar a página. A mutação invalidava
 * `['eventos']`, mas o início lê `['inicio']`, que ninguém tocava.
 *
 * <p>A causa não foi esquecimento pontual: era o desenho. Cada mutação precisava lembrar de
 * TODAS as telas derivadas (início, dashboard, relatórios, busca), e telas derivadas nascem
 * o tempo todo. Centralizar troca "lembrar em N lugares" por "declarar em um".
 *
 * <p><b>Ao criar uma tela nova que agrega dados de outras, adicione a chave dela aqui</b> —
 * é o único lugar que precisa saber.
 */
type Entidade = 'evento' | 'membro' | 'movimentacao' | 'categoria' | 'usuario' | 'igreja' | 'inscricao'

/** Prefixos de queryKey. O TanStack invalida por prefixo, então `['relatorios']` pega todas. */
const AFETADAS: Record<Entidade, string[][]> = {
  evento: [
    ['eventos'],
    ['inicio'], // próximos eventos
    ['dashboard'],
    ['busca-global'],
  ],
  membro: [
    ['membros'],
    ['usuarios'], // usuário carrega o nome do membro
    ['inicio'], // aniversariantes do mês
    ['dashboard'],
    ['busca-global'],
    ['relatorios'], // contagem de membros no consolidado da família
  ],
  movimentacao: [
    ['movimentacoes'],
    ['relatorios'],
    ['dashboard'],
    ['busca-global'],
  ],
  categoria: [
    ['categorias'],
    ['movimentacoes'], // a linha mostra o nome da categoria
    ['relatorios'], // o relatório por categoria quebra por ela
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
    ['relatorios'], // o consolidado mostra nome e composição da família
  ],
  inscricao: [
    ['inscricoes'], // minha inscrição, participantes e lista de inscritos
    ['eventos'], // cards de evento mostram vagas restantes
    ['evento'], // tela de detalhe do evento
    ['inicio'], // próximos eventos também mostram vagas
  ],
}

/**
 * Invalida tudo que depende das entidades informadas.
 *
 * @example
 * // no onSuccess de uma mutação de evento:
 * invalidarCache(queryClient, 'evento')
 */
export function invalidarCache(queryClient: QueryClient, ...entidades: Entidade[]): void {
  const chaves = entidades.flatMap((e) => AFETADAS[e])
  for (const queryKey of chaves) {
    queryClient.invalidateQueries({ queryKey })
  }
}
