'use client'

import { useState } from 'react'
import { Search, X } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import styles from './SelecaoPessoa.module.css'

interface SelecaoPessoaProps {
  pessoaIdSelecionado?: string
  nomeSelecionado?: string
  onSelecionar: (pessoaId: string | undefined, nome: string | undefined) => void
  label: string
}

export function SelecaoPessoa({ pessoaIdSelecionado, nomeSelecionado, onSelecionar, label }: SelecaoPessoaProps) {
  const [busca, setBusca] = useState('')
  const [aberto, setAberto] = useState(false)
  const [nomeCapturado, setNomeCapturado] = useState<string | undefined>(undefined)
  const buscaDebounced = useDebounce(busca, 300)

  const { data } = usePessoas({ q: buscaDebounced, page: 0, size: 8 })
  const pessoas = data?.content ?? []

  const nomeExibido = nomeCapturado ?? nomeSelecionado

  if (pessoaIdSelecionado && nomeExibido) {
    return (
      <div className={styles.chip}>
        <span className={styles.chipNome}>{nomeExibido}</span>
        <button
          type="button"
          className={styles.chipRemover}
          onClick={() => {
            setNomeCapturado(undefined)
            onSelecionar(undefined, undefined)
          }}
          aria-label="Remover"
        >
          <X size={16} />
        </button>
      </div>
    )
  }

  return (
    <div className={styles.wrapper}>
      <div className={styles.inputWrap}>
        <Search size={16} className={styles.inputIcone} />
        <input
          type="text"
          className={styles.input}
          placeholder={`Buscar ${label.toLowerCase()}...`}
          value={busca}
          onChange={(e) => { setBusca(e.target.value); setAberto(true) }}
          onFocus={() => setAberto(true)}
          onBlur={() => setTimeout(() => setAberto(false), 150)}
        />
      </div>

      {aberto && buscaDebounced && (
        <div className={styles.dropdown}>
          {pessoas.length === 0 ? (
            <div className={styles.vazio}>Nenhuma pessoa encontrada.</div>
          ) : (
            pessoas.map((p) => (
              <button
                key={p.id}
                type="button"
                className={styles.opcao}
                // onMouseDown (não onClick): dispara antes do onBlur do input fechar o
                // dropdown, evitando o "precisa clicar duas vezes" — o 1º clique fechava
                // sem selecionar. preventDefault evita que o input perca o foco antes da hora.
                onMouseDown={(e) => {
                  e.preventDefault()
                  setNomeCapturado(p.nome)
                  onSelecionar(p.id, p.nome)
                  setBusca('')
                  setAberto(false)
                }}
              >
                {p.nome}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
