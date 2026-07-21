package com.domus.api.modules.financeiro.categoria.DTOs;

/**
 * A11: quantos lançamentos usam uma categoria — pro FRONT decidir se pede confirmação ao
 * atualizar (sem lançamento associado, salva direto; com, pergunta e diz a quantidade).
 *
 * <p><b>Por que não é um campo em {@link CategoriaResponse} (o item da lista):</b> a tela de
 * lista mostra várias categorias de uma vez, e a contagem só importa no momento de EDITAR uma
 * categoria específica. Colocar a contagem no {@code CategoriaResponse} da listagem obrigaria
 * calcular um COUNT por categoria da página inteira (N+1, ou um GROUP BY caro que ninguém
 * pediu) só para um dado que 99% das renderizações da lista nunca usam. Um endpoint próprio,
 * consultado só quando o admin abre "editar", custa UM {@code COUNT(*)} indexado
 * (categoria_id + igreja_id) — o mesmo padrão de "buscarIdsPorCategoria", que já existe para
 * o caso de arquivar.
 */
public record ContagemMovimentacoesResponse(long total) {
}
