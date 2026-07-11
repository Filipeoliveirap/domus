'use client'

import { useState, useRef, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Search, X, Users, Calendar, UserCog, Wallet, Tag, Loader2 } from 'lucide-react'
import { useBuscaGlobal, type ResultadoBusca } from '@/hooks/busca/useBuscaGlobal'
import styles from './BuscaGlobal.module.css'

const TIPO_CONFIG: Record<ResultadoBusca['tipo'], {
  label: string
  icon: typeof Users
  rota: (r: ResultadoBusca) => string
}> = {
  MEMBRO:       { label: 'Membros',       icon: Users,    rota: (r) => `/membros?q=${encodeURIComponent(r.titulo)}` },
  EVENTO:       { label: 'Eventos',       icon: Calendar, rota: (r) => `/eventos?q=${encodeURIComponent(r.titulo)}` },
  USUARIO:      { label: 'Usuários',      icon: UserCog,  rota: (r) => `/usuarios?q=${encodeURIComponent(r.titulo)}` },
  MOVIMENTACAO: { label: 'Movimentações', icon: Wallet,   rota: (r) => `/financeiro/movimentacoes?q=${encodeURIComponent(r.titulo)}` },
  CATEGORIA:    { label: 'Categorias',    icon: Tag,      rota: (r) => `/financeiro/categorias?q=${encodeURIComponent(r.titulo)}` },
}

const ORDEM_TIPOS: ResultadoBusca['tipo'][] = ['MEMBRO', 'EVENTO', 'MOVIMENTACAO', 'CATEGORIA', 'USUARIO']

export function BuscaGlobal() {
  const [termo, setTermo] = useState('')
  const [aberto, setAberto] = useState(false)
  const router = useRouter()
  const wrapperRef = useRef<HTMLDivElement>(null)

  const { data: resultados, isFetching } = useBuscaGlobal(termo)

  useEffect(() => {
    function handleClickFora(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setAberto(false)
      }
    }
    document.addEventListener('mousedown', handleClickFora)
    return () => document.removeEventListener('mousedown', handleClickFora)
  }, [])

  const agrupados = (resultados ?? []).reduce((acc, r) => {
    (acc[r.tipo] ??= []).push(r)
    return acc
  }, {} as Record<ResultadoBusca['tipo'], ResultadoBusca[]>)

  const temResultados = (resultados?.length ?? 0) > 0
  const mostrarDropdown = aberto && termo.trim().length >= 2

  function selecionar(r: ResultadoBusca) {
    const rota = TIPO_CONFIG[r.tipo].rota(r)
    setTermo('')
    setAberto(false)
    router.push(rota)
  }

  return (
    <div className={styles.wrapper} ref={wrapperRef}>
      <div className={styles.inputWrap}>
        <Search size={18} className={styles.iconeBusca} />
        <input
          type="text"
          className={styles.input}
          placeholder="Buscar membros, eventos, finanças..."
          value={termo}
          onChange={(e) => { setTermo(e.target.value); setAberto(true) }}
          onFocus={() => setAberto(true)}
        />
        {isFetching && <Loader2 size={16} className={styles.spinner} />}
        {termo && !isFetching && (
          <button className={styles.limpar} onClick={() => { setTermo(''); setAberto(false) }}>
            <X size={16} />
          </button>
        )}
      </div>

      {mostrarDropdown && (
        <div className={styles.dropdown}>
          {!temResultados && !isFetching && (
            <div className={styles.vazio}>Nenhum resultado para “{termo}”.</div>
          )}

          {ORDEM_TIPOS.map((tipo) => {
            const itens = agrupados[tipo]
            if (!itens || itens.length === 0) return null
            const config = TIPO_CONFIG[tipo]
            const Icon = config.icon

            return (
              <div key={tipo} className={styles.grupo}>
                <div className={styles.grupoHeader}>
                  <Icon size={14} />
                  <span>{config.label}</span>
                </div>
                {itens.map((r) => (
                  <button key={r.id} className={styles.item} onClick={() => selecionar(r)}>
                    <span className={styles.itemTitulo}>{r.titulo}</span>
                    <span className={styles.itemSubtitulo}>{r.subtitulo}</span>
                  </button>
                ))}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}