// Sem cache-busting de propósito: id de foto nunca é reaproveitado, resposta vem com Cache-Control: immutable.
export function urlFoto(id: string | null | undefined, tamanho: 'THUMB' | 'DISPLAY' = 'DISPLAY') {
  return id ? `/api/fotos/${id}?tamanho=${tamanho}` : null
}
