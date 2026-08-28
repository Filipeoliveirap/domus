import { useEffect, useRef, useState } from 'react'

const DURACAO_SAIDA = 260
const SEP = ' '

export type EntradaLista<T> = { item: T; chave: string; saindo: boolean }

/**
 * Mantém no resultado, por ~0.26s e marcados `saindo`, os itens que sumiram da lista de
 * origem — pra o `<ItemAnimado>` rodar a animação de saída antes de desmontar.
 *
 * Ordem: itens atuais (na ordem da origem) primeiro, depois os que estão saindo.
 * O reconcile reage só à MUDANÇA do conjunto de chaves (não a cada render), então é
 * seguro passar um array/extrator novos toda renderização.
 */
export function useListaComSaida<T>(itens: T[], chave: (item: T) => string): EntradaLista<T>[] {
  const prevItensRef = useRef<T[]>(itens)
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>())
  const [saindo, setSaindo] = useState<Array<{ chave: string; item: T }>>([])

  const assinatura = itens.map(chave).join(SEP)

  // Reage só à mudança do conjunto de chaves. Roda ANTES do efeito de snapshot abaixo,
  // então prevItensRef ainda tem os itens do render anterior (com o que acabou de sair).
  useEffect(() => {
    const agora = new Set(itens.map(chave))
    const partidos = prevItensRef.current.filter((i) => !agora.has(chave(i)))

    if (partidos.length > 0) {
      setSaindo((s) => {
        const jaSaindo = new Set(s.map((e) => e.chave))
        const novos = partidos
          .filter((i) => !jaSaindo.has(chave(i)))
          .map((i) => ({ chave: chave(i), item: i }))
        return novos.length > 0 ? [...s, ...novos] : s
      })
      for (const i of partidos) {
        const k = chave(i)
        if (!timers.current.has(k)) {
          const t = setTimeout(() => {
            timers.current.delete(k)
            setSaindo((s) => s.filter((e) => e.chave !== k))
          }, DURACAO_SAIDA)
          timers.current.set(k, t)
        }
      }
    }

    // itens que voltaram (re-add durante a saída): cancela o timer e tira de `saindo`
    setSaindo((s) => {
      const filtrado = s.filter((e) => {
        if (!agora.has(e.chave)) return true
        const t = timers.current.get(e.chave)
        if (t) {
          clearTimeout(t)
          timers.current.delete(e.chave)
        }
        return false
      })
      return filtrado.length === s.length ? s : filtrado
    })
    // Só a assinatura (conjunto+ordem de chaves) dispara o reconcile.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [assinatura])

  useEffect(() => {
    prevItensRef.current = itens
  })

  useEffect(() => {
    const m = timers.current
    return () => {
      m.forEach(clearTimeout)
      m.clear()
    }
  }, [])

  const vivos: EntradaLista<T>[] = itens.map((item) => ({ item, chave: chave(item), saindo: false }))
  const saindoEntradas: EntradaLista<T>[] = saindo.map((e) => ({ item: e.item, chave: e.chave, saindo: true }))

  return [...vivos, ...saindoEntradas]
}
