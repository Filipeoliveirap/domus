'use client'

import { useRef, useState, useMemo } from 'react'
import { Search } from 'lucide-react'
import { clsx } from 'clsx'
import { useDebounce } from '@/hooks/useDebounce'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import type { BeneficiarioInput } from '@/types/contas-a-pagar'
import styles from './BeneficiarioField.module.css'

interface BeneficiarioFieldProps {
  value: BeneficiarioInput
  onChange: (v: BeneficiarioInput) => void
  erroPessoa?: string
  erroTexto?: string
  label?: string
  disabled?: boolean
}

const TAMANHO_BUSCA = 10

export function BeneficiarioField({
  value,
  onChange,
  erroPessoa,
  erroTexto,
  label = 'Beneficiário',
  disabled,
}: BeneficiarioFieldProps) {
  const modoPessoa = value.pessoaId !== null || !value.texto
  const [busca, setBusca] = useState('')
  const [menuAberto, setMenuAberto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const debouncedBusca = useDebounce(busca, 200)

  const { data: pessoas } = usePessoas({
    q: debouncedBusca,
    page: 0,
    size: TAMANHO_BUSCA,
    vinculo: '',
  })

  // Deriva o nome da pessoa selecionada a partir da lista quando o prop muda
  const nomeDaPessoaSelecionada = useMemo(() => {
    if (!value.pessoaId) return ''
    const encontrada = pessoas?.content.find((p) => p.id === value.pessoaId)
    return encontrada?.nome ?? ''
  }, [value.pessoaId, pessoas?.content])

  // Atualiza o nome visivel quando o prop muda ou a busca retorna
  const nomeVisivel = nomeDaPessoaSelecionada || (modoPessoa ? '' : value.texto ?? '')

  function selecionarPessoa(p: { id: string; nome: string }) {
    onChange({ pessoaId: p.id, texto: null })
    setBusca('')
    setMenuAberto(false)
  }

  function setarNomeAvulso(texto: string) {
    onChange({ pessoaId: null, texto })
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const novo = e.target.value
    setBusca(novo)
    setMenuAberto(true)
    if (value.pessoaId) {
      onChange({ pessoaId: null, texto: null })
    }
  }

  return (
    <div ref={ref} className={styles.campo}>
      {label && <label className={styles.label}>{label}</label>}

      {/* Toggle */}
      <div className={styles.toggle}>
        <button
          type="button"
          className={clsx(styles.toggleBtn, modoPessoa && styles.toggleAtivo)}
          onClick={() => {
            onChange({ pessoaId: value.pessoaId, texto: null })
          }}
          disabled={disabled}
        >
          Pessoa cadastrada
        </button>
        <button
          type="button"
          className={clsx(styles.toggleBtn, !modoPessoa && styles.toggleAtivo)}
          onClick={() => {
            onChange({ pessoaId: null, texto: value.texto ?? '' })
          }}
          disabled={disabled}
        >
          Nome avulso
        </button>
      </div>

      {modoPessoa ? (
        <div className={styles.buscaWrapper}>
          <div className={styles.buscaInput}>
            <Search size={16} className={styles.buscaIcone} aria-hidden="true" />
            <input
              type="text"
              placeholder="Buscar pessoa..."
              value={nomeVisivel}
              onChange={handleInputChange}
              onFocus={() => setMenuAberto(true)}
              className={clsx(erroPessoa && styles.erro)}
              disabled={disabled}
              aria-label="Buscar pessoa cadastrada"
            />
          </div>
          {erroPessoa && <span className={styles.erroMsg}>{erroPessoa}</span>}

          {menuAberto && busca.length > 0 && pessoas?.content.length !== 0 && (
            <ul className={styles.menu} role="listbox">
              {pessoas?.content.map((p) => (
                <li key={p.id}>
                  <button
                    type="button"
                    className={styles.menuItem}
                    onClick={() => selecionarPessoa(p)}
                  >
                    <span className={styles.menuNome}>{p.nome}</span>
                    {p.email && <span className={styles.menuEmail}>{p.email}</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <div>
          <input
            type="text"
            placeholder="Nome do beneficiário sem cadastro..."
            value={value.texto ?? ''}
            onChange={(e) => setarNomeAvulso(e.target.value)}
            className={clsx(styles.input, erroTexto && styles.erro)}
            disabled={disabled}
            aria-label="Nome do beneficiário avulso"
          />
          {erroTexto && <span className={styles.erroMsg}>{erroTexto}</span>}
        </div>
      )}
    </div>
  )
}
