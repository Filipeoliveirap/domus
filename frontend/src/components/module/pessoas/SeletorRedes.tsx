'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Search, X } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { useMinisterios } from '@/hooks/ministerio/useMinisterios'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import styles from './PessoaForm.module.css'

interface SeletorRedesProps {
  selecionadas: Set<string>
  onChange: (selecionadas: Set<string>) => void
}

/**
 * Cadastro de pessoa é o outro lugar (além da própria tela de {@link ROTULO_MINISTERIO_PLURAL})
 * onde dá pra colocar alguém numa rede — útil pra quem já cadastra a pessoa sabendo de cara
 * onde ela vai servir, sem precisar abrir a tela de detalhe da rede depois.
 *
 * Busca em vez de lista fixa: numa igreja com muitas redes cadastradas, rolar uma lista de
 * checkboxes fica ruim rápido — digitar o nome é mais direto. As já selecionadas viram chips
 * removíveis, e a busca só afeta o que ainda pode ser adicionado.
 */
export function SeletorRedes({ selecionadas, onChange }: SeletorRedesProps) {
  const { data: redes = [], isLoading } = useMinisterios()
  const [busca, setBusca] = useState('')

  function adicionar(id: string) {
    onChange(new Set(selecionadas).add(id))
  }

  function remover(id: string) {
    const novoConjunto = new Set(selecionadas)
    novoConjunto.delete(id)
    onChange(novoConjunto)
  }

  if (isLoading) return null

  if (redes.length === 0) {
    return (
      <div className={styles.redesWrap}>
        <span className={styles.labelRedes}>{ROTULO_MINISTERIO_PLURAL.toUpperCase()}</span>
        <p className={styles.redesVazio}>
          Nenhuma {ROTULO_MINISTERIO.toLowerCase()} cadastrada ainda. É preciso{' '}
          <Link href="/ministerios" className={styles.redesLink}>
            criar uma {ROTULO_MINISTERIO.toLowerCase()}
          </Link>{' '}
          antes de colocar alguém nela.
        </p>
      </div>
    )
  }

  const redesSelecionadas = redes.filter((rede) => selecionadas.has(rede.id))

  const buscaNormalizada = busca.trim().toLowerCase()
  const resultados = buscaNormalizada
    ? redes.filter((rede) => !selecionadas.has(rede.id) && rede.nome.toLowerCase().includes(buscaNormalizada))
    : []

  return (
    <div className={styles.redesWrap}>
      <span className={styles.labelRedes}>{ROTULO_MINISTERIO_PLURAL.toUpperCase()}</span>

      {redesSelecionadas.length > 0 && (
        <div className={styles.redesChips}>
          {redesSelecionadas.map((rede) => (
            <span key={rede.id} className={styles.redeChip}>
              {rede.nome}
              <button
                type="button"
                className={styles.redeChipRemover}
                onClick={() => remover(rede.id)}
                aria-label={`Remover ${rede.nome}`}
              >
                <X size={14} />
              </button>
            </span>
          ))}
        </div>
      )}

      <Input
        id="busca-redes"
        placeholder={`Buscar ${ROTULO_MINISTERIO.toLowerCase()} pelo nome…`}
        leftIcon={<Search size={16} />}
        value={busca}
        onChange={(e) => setBusca(e.target.value)}
      />

      {buscaNormalizada && (
        resultados.length === 0 ? (
          <p className={styles.redesVazio}>
            Nenhuma {ROTULO_MINISTERIO.toLowerCase()} encontrada para &quot;{busca.trim()}&quot;.
          </p>
        ) : (
          <ul className={styles.redesResultados}>
            {resultados.map((rede) => (
              <li key={rede.id}>
                <button
                  type="button"
                  className={styles.redeResultado}
                  onClick={() => { adicionar(rede.id); setBusca('') }}
                >
                  {rede.nome}
                </button>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  )
}
