'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Search, X } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useMinisterios } from '@/hooks/ministerio/useMinisterios'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import styles from './PessoaForm.module.css'

interface SeletorRedesProps {
  selecionadas: Set<string>
  onChange: (selecionadas: Set<string>) => void
}

export function SeletorRedes({ selecionadas, onChange }: SeletorRedesProps) {
  const { data: redes = [], isLoading } = useMinisterios()
  const { ministerio } = useRotulos()
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
        <span className={styles.labelRedes}>{ministerio.plural.toUpperCase()}</span>
        <p className={styles.redesVazio}>
          Nenhuma {ministerio.singular.toLowerCase()} cadastrada ainda. É preciso{' '}
          <Link href="/ministerios" className={styles.redesLink}>
            criar uma {ministerio.singular.toLowerCase()}
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
      <span className={styles.labelRedes}>{ministerio.plural.toUpperCase()}</span>

      {redesSelecionadas.length > 0 && (
        <Transicao className={styles.redesChips} modo="subir">
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
        </Transicao>
      )}

      <Input
        id="busca-redes"
        placeholder={`Buscar ${ministerio.singular.toLowerCase()} pelo nome…`}
        leftIcon={<Search size={16} />}
        value={busca}
        onChange={(e) => setBusca(e.target.value)}
      />

      {buscaNormalizada && (
        <Transicao key={buscaNormalizada} modo="subir">
          {resultados.length === 0 ? (
            <p className={styles.redesVazio}>
              Nenhuma {ministerio.singular.toLowerCase()} encontrada para &quot;{busca.trim()}&quot;.
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
          )}
        </Transicao>
      )}
    </div>
  )
}
