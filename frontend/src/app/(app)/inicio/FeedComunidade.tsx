'use client'

import { useState } from 'react'
import { MessageSquare } from 'lucide-react'
import { useFeedPostagens } from '@/hooks/postagem/useFeedPostagens'
import { PostItem } from './PostItem'
import { CaixaCriarPostagem } from './CaixaCriarPostagem'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { Transicao } from '@/components/common/Transicao/Transicao'
import type { TipoPostagem } from '@/types/postagem.type'
import styles from './FeedComunidade.module.css'

const ABAS_FILTRO: { label: string; valor?: TipoPostagem }[] = [
  { label: 'Tudo' },
  { label: 'Devocionais', valor: 'DEVOCIONAL' },
  { label: 'Resumos da Semana', valor: 'RESUMO_CULTO' },
  { label: 'Pedidos de Oração', valor: 'PEDIDO_ORACAO' },
  { label: 'Testemunhos', valor: 'TESTEMUNHO' },
]

export function FeedComunidade() {
  const [tipoFilter, setTipoFilter] = useState<TipoPostagem | undefined>(undefined)
  const { data, isLoading } = useFeedPostagens(tipoFilter)

  const posts = data?.content ?? []

  return (
    <div className={styles.containerFeed}>
      <CaixaCriarPostagem />

      <div className={styles.abasFiltro}>
        {ABAS_FILTRO.map((aba) => (
          <button
            key={aba.label}
            type="button"
            className={`${styles.abaItem} ${
              tipoFilter === aba.valor ? styles.abaItemActive : ''
            }`}
            onClick={() => setTipoFilter(aba.valor)}
          >
            {aba.label}
          </button>
        ))}
      </div>

      <Transicao key={isLoading ? 'loading' : posts.length ? 'posts' : 'vazio'} modo="subir">
        {isLoading ? (
          <div className={styles.listaPosts}>
            <Skeleton style={{ height: 180, borderRadius: 16 }} />
            <Skeleton style={{ height: 180, borderRadius: 16 }} />
          </div>
        ) : posts.length === 0 ? (
          <EstadoVazio
            icone={MessageSquare}
            titulo="Nenhuma postagem no feed"
            mensagem="Seja o primeiro a compartilhar um devocional ou testemunho com a comunidade."
          />
        ) : (
          <div className={styles.listaPosts}>
            {posts.map((p) => (
              <PostItem key={p.id} postagem={p} />
            ))}
          </div>
        )}
      </Transicao>
    </div>
  )
}
