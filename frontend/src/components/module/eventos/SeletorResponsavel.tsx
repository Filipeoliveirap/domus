'use client'

import { useState } from 'react'
import { Search, X, Check } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import { Transicao } from '@/components/common/Transicao/Transicao'
import styles from './SeletorResponsavel.module.css'

interface SeletorResponsavelProps {
  /** Ids das pessoas responsáveis já escolhidas. */
  ids: string[]
  /** Nomes de quem já era responsável (edição), pra montar os chips sem buscar de novo. */
  iniciais?: { id: string; nome: string }[]
  onChange: (ids: string[]) => void
}

export function SeletorResponsavel({ ids, iniciais = [], onChange }: SeletorResponsavelProps) {
  const [busca, setBusca] = useState('')
  const buscaDebounced = useDebounce(busca, 300)
  const habilitado = buscaDebounced.trim().length >= 2
  const { data, isLoading } = usePessoas({ q: habilitado ? buscaDebounced : '', page: 0, size: 8 })

  // Nome de cada id: dos iniciais (edição) + o que a pessoa foi escolhendo nesta sessão.
  const [nomesEscolhidos, setNomesEscolhidos] = useState<Record<string, string>>({})
  const nomePorId: Record<string, string> = {
    ...Object.fromEntries(iniciais.map((r) => [r.id, r.nome])),
    ...nomesEscolhidos,
  }

  const escolhidos = new Set(ids)
  const resultados = habilitado ? (data?.content ?? []).filter((p) => !escolhidos.has(p.id)) : []

  function adicionar(p: { id: string; nome: string }) {
    setNomesEscolhidos((m) => ({ ...m, [p.id]: p.nome }))
    onChange([...ids, p.id])
    setBusca('')
  }
  function remover(id: string) {
    onChange(ids.filter((v) => v !== id))
  }

  return (
    <div className={styles.campo}>
      <span className={styles.label}>
        RESPONSÁVEIS <span className={styles.opcional}>(opcional)</span>
      </span>

      {ids.length > 0 && (
        <div className={styles.chips}>
          {ids.map((id) => (
            <Transicao key={id} modo="escala" className={styles.chip}>
              <span className={styles.chipNome}>{nomePorId[id] ?? 'Responsável'}</span>
              <button
                type="button"
                className={styles.chipRemover}
                onClick={() => remover(id)}
                aria-label={`Remover ${nomePorId[id] ?? 'responsável'}`}
              >
                <X size={16} />
              </button>
            </Transicao>
          ))}
        </div>
      )}

      <div className={styles.buscaWrap}>
        <Search size={16} className={styles.buscaIcone} aria-hidden="true" />
        <input
          type="text"
          className={styles.buscaInput}
          placeholder="Buscar pessoa pelo nome"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      </div>

      {habilitado && (
        <Transicao key={buscaDebounced} modo="subir" className={styles.resultados}>
          {isLoading ? (
            <p className={styles.aviso}>Buscando…</p>
          ) : resultados.length === 0 ? (
            <p className={styles.aviso}>Ninguém novo encontrado com esse nome.</p>
          ) : (
            resultados.map((p) => (
              <button key={p.id} type="button" className={styles.opcao} onClick={() => adicionar(p)}>
                <span>{p.nome}</span>
                <Check size={15} className={styles.opcaoCheck} aria-hidden="true" />
              </button>
            ))
          )}
        </Transicao>
      )}
    </div>
  )
}
