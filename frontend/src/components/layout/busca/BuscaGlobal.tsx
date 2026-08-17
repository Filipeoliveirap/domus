'use client'

import { useState, useRef, useEffect } from 'react'
import { useRouter, usePathname } from 'next/navigation'
import { Search, X, Users, Calendar, UserCog, Wallet, Tag, Loader2, Home, UserPlus, Network } from 'lucide-react'
import { useBuscaGlobal, type ResultadoBusca } from '@/hooks/busca/useBuscaGlobal'
import styles from './BuscaGlobal.module.css'

const TIPO_CONFIG: Record<ResultadoBusca['tipo'], {
  label: string
  icon: typeof Users
  rota: (r: ResultadoBusca) => string
}> = {
  PESSOA:       { label: 'Pessoa',        icon: Users,    rota: (r) => `/pessoas?q=${encodeURIComponent(r.titulo)}` },
  EVENTO:       { label: 'Eventos',       icon: Calendar, rota: (r) => `/eventos?q=${encodeURIComponent(r.titulo)}` },
  USUARIO:      { label: 'Usuários',      icon: UserCog,  rota: (r) => `/usuarios?q=${encodeURIComponent(r.titulo)}` },
  MOVIMENTACAO: { label: 'Movimentações', icon: Wallet,   rota: (r) => `/financeiro/movimentacoes?q=${encodeURIComponent(r.titulo)}` },
  CATEGORIA:    { label: 'Categorias',    icon: Tag,      rota: (r) => `/financeiro/categorias?q=${encodeURIComponent(r.titulo)}` },
  CELULA:       { label: 'Células',       icon: Home,     rota: (r) => `/celulas?q=${encodeURIComponent(r.titulo)}` },
  VISITANTE:    { label: 'Visitantes',    icon: UserPlus, rota: (r) => r.celulaId
                    ? `/celulas/${r.celulaId}?visitante=${r.id}`
                    : `/pessoas/visitantes?q=${encodeURIComponent(r.titulo)}` },
  MINISTERIO:   { label: 'Redes',         icon: Network,  rota: (r) => `/ministerios?q=${encodeURIComponent(r.titulo)}` },
}

const ORDEM_TIPOS: ResultadoBusca['tipo'][] = ['PESSOA', 'EVENTO', 'VISITANTE', 'CELULA', 'MINISTERIO', 'MOVIMENTACAO', 'CATEGORIA', 'USUARIO']

export function BuscaGlobal() {
  const [termo, setTermo] = useState('')
  const [aberto, setAberto] = useState(false)
  const router = useRouter()
  const pathname = usePathname()
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
    const [novoPathname] = rota.split('?')
    setTermo('')
    setAberto(false)
    if (novoPathname === pathname) {
      // Já estando na mesma rota, router.push só troca a querystring — as páginas são
      // client-side (estado lido uma vez na montagem, ex.: useState(() => searchParams...)),
      // então nada reage. router.refresh() também não ajuda (só refaz Server Components).
      // Só um reload de verdade remonta a página com o novo termo.
      window.location.href = rota
    } else {
      router.push(rota)
    }
  }

  return (
    <div className={styles.wrapper} ref={wrapperRef}>
      <div className={styles.inputWrap}>
        <Search size={18} className={styles.iconeBusca} />
        <input
          type="text"
          className={styles.input}
          placeholder="Buscar pessoas, eventos, finanças..."
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