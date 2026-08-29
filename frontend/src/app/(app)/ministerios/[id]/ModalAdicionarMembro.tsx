'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { Search, X } from 'lucide-react'
import { clsx } from 'clsx'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useAdicionarMembro } from '@/hooks/ministerio/useMembroMinisterio'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { Input } from '@/components/common/input/Input'
import styles from './detalhe.module.css'

interface Props {
  ministerioId: string
  membrosAtuaisIds: Set<string>
  onClose: () => void
}

// "Quick pick" sem paginação de propósito: page: 0 fixo, sem UI de próxima página.
export function ModalAdicionarMembro({ ministerioId, membrosAtuaisIds, onClose }: Props) {
  const [busca, setBusca] = useState('')
  const buscaDebounced = useDebounce(busca, 300)
  const inputRef = useRef<HTMLInputElement>(null)
  const { data } = usePessoas({ q: buscaDebounced, page: 0 })
  const adicionar = useAdicionarMembro(ministerioId)
  const { saindo, fechar } = useFecharAnimado(onClose, 260)
  // pessoaIds sendo adicionadas — cada linha colapsa animada, independente. Pode adicionar
  // várias rápido em sequência.
  const [adicionando, setAdicionando] = useState<Set<string>>(() => new Set())
  // ids já adicionados nesta sessão do modal — filtra a lista pra sempre (não espera o
  // refetch). Sem isso, se o refetch demora mais que o timer, a linha "pisca de volta".
  const [jaAdicionados, setJaAdicionados] = useState<Set<string>>(() => new Set())
  const semId = (s: Set<string>, id: string) => {
    const n = new Set(s)
    n.delete(id)
    return n
  }

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape') fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  const resultados = (data?.content ?? []).filter((p) => !membrosAtuaisIds.has(p.id) && !jaAdicionados.has(p.id))

  function selecionar(pessoaId: string) {
    if (adicionando.has(pessoaId)) return // só ignora re-clique na MESMA pessoa
    setAdicionando((s) => new Set(s).add(pessoaId))
    setTimeout(() => {
      adicionar.mutate(pessoaId, {
        onSuccess: () => {
          setJaAdicionados((s) => new Set(s).add(pessoaId))
          setTimeout(() => setAdicionando((s) => semId(s, pessoaId)), 350)
        },
        onError: () => setAdicionando((s) => semId(s, pessoaId)),
      })
    }, 400)
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <button type="button" className={styles.fechar} onClick={fechar} aria-label="Fechar">
          <X size={18} />
        </button>

        <div className={styles.buscaWrap}>
          <Input
            ref={inputRef}
            id="busca-membro-ministerio"
            autoFocus
            placeholder="Buscar pessoa por nome"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            leftIcon={<Search size={18} />}
          />
        </div>
        <ul className={styles.listaResultados}>
          {resultados.map((pessoa) => (
            <li
              key={pessoa.id}
              className={clsx(styles.itemResultado, adicionando.has(pessoa.id) && styles.itemResultadoSaindo)}
              onClick={() => selecionar(pessoa.id)}
            >
              {urlFoto(pessoa.fotoId, 'THUMB') ? (
                <Image src={urlFoto(pessoa.fotoId, 'THUMB')!} alt="" width={32} height={32} unoptimized className={styles.avatar} />
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
