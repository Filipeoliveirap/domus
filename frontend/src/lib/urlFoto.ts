/**
 * URL da foto servida pelo próprio Domus.
 *
 * <p>Não é uma URL de storage: o bucket é privado e a imagem passa pela API, que valida
 * sessão e igreja. Por isso `img-src 'self'` da CSP cobre — nenhum domínio novo a liberar.
 *
 * <p>Sem cache-busting de propósito: um id de foto nunca é reaproveitado (upload novo =
 * id novo), então a resposta vem com `Cache-Control: immutable` — o navegador busca uma
 * vez e nunca mais revalida. Colar um `?v=...` aqui destruiria exatamente essa economia.
 */
export function urlFoto(id: string | null | undefined, tamanho: 'THUMB' | 'DISPLAY' = 'DISPLAY') {
  return id ? `/api/fotos/${id}?tamanho=${tamanho}` : null
}
