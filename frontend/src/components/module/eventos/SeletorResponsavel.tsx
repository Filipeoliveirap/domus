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

/**
 * Busca uma pessoa pelo nome e a define como responsável do evento. Reusa o mesmo
 * `usePessoas` + debounce do <ModalInscreverPessoas> — não é uma segunda forma de listar
 * pessoas, é a mesma.
 *
 * <p>Enquanto há um responsável escolhido, mostra só o "chip" dele com um X; a busca só
 * reaparece ao remover. Assim a tela não fica com uma lista aberta o tempo todo.
 */
export function SeletorResponsavel({ valor, nomeInicial, onChange }: SeletorResponsavelProps) {
  const [busca, setBusca] = useState('')
  // Nome a exibir no chip. Começa com o do evento em edição e passa a refletir a última
  // escolha — sem isto, escolher uma pessoa nova mostraria o nome inicial (ou um texto
  // genérico), porque o pai só devolve o id de volta.
  const [nomeEscolhido, setNomeEscolhido] = useState<string | undefined>(nomeInicial)
  const buscaDebounced = useDebounce(busca, 300)
  // Só busca quando não há ninguém escolhido E o termo tem substância: uma letra traria
  // meia igreja e nenhuma utilidade.
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
