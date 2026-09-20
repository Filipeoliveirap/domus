/**
 * Função pura de normalização de apelido. Espelha o que o back faz:
 * - trim nas pontas
 * - colapsa espaços múltiplos no meio
 * - trunca em 60 chars (mesmo limite do VARCHAR(60) da migration)
 * - mantém acentos e emoji
 *
 * Por que duplicar no front se o back já faz? Defesa em profundidade + latência.
 * Se o usuário digita 500 chars num campo, mostrar erro local antes de ir e voltar
 * da API é UX melhor do que esperar o 400. Mas isso não substitui a validação no
 * back — front pode ser burlado.
 */
export function formatarApelido(input: string): string {
  const trimado = input.trim();
  if (trimado === "") return "";

  const colapsado = trimado.replace(/\s+/g, " ");
  return colapsado.slice(0, 60);
}
