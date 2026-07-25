'use client'

import { useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import { useAdicionarMembro } from '@/hooks/ministerio/useMembroMinisterio'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './detalhe.module.css'

interface Props {
  ministerioId: string
  membrosAtuaisIds: Set<string>
  onClose: () => void
}

/**
 * Busca + seleção única de pessoa, mesmo padrão de `ModalInscreverPessoas.tsx` (usePessoas +
 * useDebounce), mas de seleção única e sem paginação — é um "quick pick", não um navegador
 * completo (por isso `page: 0` fixo, sem UI de próxima página).
 *
 * useAdicionarMembro já dispara notificar.sucesso/erro sozinho (Task 9) — aqui só fecha o
 * modal em caso de sucesso; em erro, o modal continua aberto (toast já informou o motivo).
 */
export function ModalAdicionarMembro({ ministerioId, membrosAtuaisIds, onClose }: Props) {
  const [busca, setBusca] = useState('')
  const buscaDebounced = useDebounce(busca, 300)
  const inputRef = useRef<HTMLInputElement>(null)
  const { data } = usePessoas({ q: buscaDebounced, page: 0 })
  const adicionar = useAdicionarMembro(ministerioId)

  async function selecionar(pessoaId: string) {
    try {
      await adicionar.mutateAsync(pessoaId)
      onClose()
    } catch {
      // erro já notificado pela mutation.
    }
  }

  const resultados = (data?.content ?? []).filter((p) => !membrosAtuaisIds.has(p.id))

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.buscaWrap}>
          <Input
            ref={inputRef}
            id="busca-membro-ministerio"
            autoFocus
            placeholder="Buscar pessoa por nome"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            leftIcon={<Search size={18} />}
            rightElement={
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={onClose}
                aria-label="Fechar"
                className={styles.botaoFechar}
              >
                <X size={18} />
              </Button>
            }
          />
        </div>
        <ul className={styles.listaResultados}>
          {resultados.map((pessoa) => (
            <li key={pessoa.id} className={styles.itemResultado} onClick={() => selecionar(pessoa.id)}>
              {urlFoto(pessoa.fotoId, 'THUMB') ? (
                // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                <img src={urlFoto(pessoa.fotoId, 'THUMB')!} alt="" className={styles.avatar} />
              ) : (
                <span className={styles.avatarIniciais}>{iniciais(pessoa.nome)}</span>
              )}
              <span>{pessoa.nome}</span>
            </li>
          ))}
          {resultados.length === 0 && (
            <li className={styles.semResultado}>Nenhuma pessoa encontrada.</li>
          )}
        </ul>
      </div>
    </div>
  )
}
