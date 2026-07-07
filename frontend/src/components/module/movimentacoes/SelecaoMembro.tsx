'use client'

import { useState } from 'react'
import { Search, X } from 'lucide-react'
import { useMembros } from '@/hooks/membro/useMembros'
import { useDebounce } from '@/hooks/useDebounce'
import styles from './SelecaoMembro.module.css'

interface SelecaoMembroProps {
  membroIdSelecionado?: string
  nomeSelecionado?: string
  onSelecionar: (membroId: string | undefined, nome: string | undefined) => void
  label: string
}

export function SelecaoMembro({ membroIdSelecionado, nomeSelecionado, onSelecionar, label }: SelecaoMembroProps) {
  const [busca, setBusca] = useState('')
  const [aberto, setAberto] = useState(false)
  const [nomeCapturado, setNomeCapturado] = useState<string | undefined>(undefined)
  const buscaDebounced = useDebounce(busca, 300)

  const { data } = useMembros({ q: buscaDebounced, page: 0, size: 8 })
  const membros = data?.content ?? []

  const nomeExibido = nomeCapturado ?? nomeSelecionado

  if (membroIdSelecionado && nomeExibido) {
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
          {membros.length === 0 ? (
            <div className={styles.vazio}>Nenhum membro encontrado.</div>
          ) : (
            membros.map((m) => (
              <button
                key={m.id}
                type="button"
                className={styles.opcao}
                onClick={() => {
                  setNomeCapturado(m.nome)
                  onSelecionar(m.id, m.nome)
                  setBusca('')
                  setAberto(false)
                }}
              >
                {m.nome}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}