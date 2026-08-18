'use client'

import { useState } from 'react'
import { Search, X, Check } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import styles from './SeletorResponsavel.module.css'

interface SeletorResponsavelProps {
  /** Id da pessoa responsável já escolhida (ou undefined). */
  valor?: string
  /** Nome a exibir quando já há um responsável — vem do evento em edição, para não
   *  precisar buscar de novo só para mostrar quem já está escolhido. */
  nomeInicial?: string
  onChange: (pessoaId: string | undefined, nome: string | undefined) => void
}

export function SeletorResponsavel({ valor, nomeInicial, onChange }: SeletorResponsavelProps) {
  const [busca, setBusca] = useState('')
  const [nomeEscolhido, setNomeEscolhido] = useState<string | undefined>(nomeInicial)
  const buscaDebounced = useDebounce(busca, 300)
  const habilitado = !valor && buscaDebounced.trim().length >= 2
  const { data, isLoading } = usePessoas({
    q: habilitado ? buscaDebounced : '',
    page: 0,
    size: 8,
  })

  const resultados = habilitado ? (data?.content ?? []) : []

  if (valor) {
    return (
      <div className={styles.campo}>
        <span className={styles.label}>RESPONSÁVEL</span>
        <div className={styles.chip}>
          <span className={styles.chipNome}>{nomeEscolhido ?? 'Responsável selecionado'}</span>
          <button
            type="button"
            className={styles.chipRemover}
            onClick={() => { onChange(undefined, undefined); setNomeEscolhido(undefined); setBusca('') }}
            aria-label="Remover responsável"
          >
            <X size={16} />
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.campo}>
      <span className={styles.label}>RESPONSÁVEL <span className={styles.opcional}>(opcional)</span></span>
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
        <div className={styles.resultados}>
          {isLoading ? (
            <p className={styles.aviso}>Buscando…</p>
          ) : resultados.length === 0 ? (
            <p className={styles.aviso}>Ninguém encontrado com esse nome.</p>
          ) : (
            resultados.map((p) => (
              <button
                key={p.id}
                type="button"
                className={styles.opcao}
                onClick={() => { onChange(p.id, p.nome); setNomeEscolhido(p.nome); setBusca('') }}
              >
                <span>{p.nome}</span>
                <Check size={15} className={styles.opcaoCheck} aria-hidden="true" />
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
