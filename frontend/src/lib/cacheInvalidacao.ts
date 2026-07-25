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
type Entidade = 'evento' | 'pessoa' | 'movimentacao' | 'categoria' | 'usuario' | 'igreja' | 'inscricao' | 'localEvento' | 'ministerio'

/** Prefixos de queryKey. O TanStack invalida por prefixo, então `['relatorios']` pega todas. */
const AFETADAS: Record<Entidade, string[][]> = {
  evento: [
    ['eventos'],
    // ATENÇÃO: `['eventos']` (lista) NÃO cobre `['evento', id]` (detalhe) — a invalidação é
    // por prefixo, e "evento" não é prefixo de "eventos". Faltando esta linha, o detalhe
    // seguia mostrando o evento velho depois de editado. Chave nova de evento entra aqui.
    ['evento'],
    ['inicio'], // próximos eventos
    ['dashboard'],
    ['busca-global'],
    // Rodada 2 (F12): restringir um evento (exclusivoMembros) cancela
    // inscrições no backend, mas a tela de detalhe continuava mostrando a lista velha —
    // atualizar um evento também precisa invalidar quem está inscrito nele.
    ['inscricoes'],
  ],
  pessoa: [
    ['pessoas'],
    // ATENÇÃO (mesma armadilha do evento acima): `['pessoas']` (lista) NÃO cobre
    // `['pessoa', id]` (detalhe) — a invalidação é por prefixo. Faltando esta linha, o
    // detalhe seguiria mostrando a pessoa velha depois de editada.
    ['pessoa'],
    ['usuarios'], // usuário carrega o nome da pessoa
    ['inicio'], // aniversariantes do mês
    ['dashboard'],
    ['busca-global'],
    ['relatorios'], // contagem de membros/congregantes no consolidado da família
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
    // Task 10: `['elegibilidade', eventoId]` NÃO é coberto por `['inscricoes']` (prefixo
    // diferente) — sem esta linha o botão de inscrição continuaria mostrando o impedimento
    // velho (ex.: vagas esgotadas) depois de uma inscrição ou cancelamento.
    ['elegibilidade'],
  ],
  // ['eventos', 'locais'] (useLocaisEvento/SeletorLocal) fica sob o prefixo ['eventos'],
  // já invalidado — mas o CARD de evento mostra o nome/endereço do local, então também
  // precisa recarregar a lista e o detalhe de eventos.
  localEvento: [
    ['eventos'],
    ['evento'],
  ],
  ministerio: [
    ['ministerios'],
    // ATENÇÃO (mesma armadilha do evento/pessoa acima): `['ministerios']` (lista) NÃO cobre
    // `['ministerios', id]` (detalhe) — invalidação é por prefixo, e o id não é prefixo da
    // lista. As duas entradas são necessárias.
    ['pessoas'], // a seção "Ministérios" do perfil de pessoa também fica velha
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
