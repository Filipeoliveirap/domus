'use client'

import { useState } from 'react'
import { MessageSquare } from 'lucide-react'
import { useMutationState } from '@tanstack/react-query'
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

function PostCardSkeleton() {
  return (
    <div
      style={{
        background: 'var(--bg-surface, #ffffff)',
        borderRadius: 'var(--radius-card, 16px)',
        border: '1px solid var(--border-color, rgba(115, 118, 134, 0.15))',
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Skeleton circle height="40px" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <Skeleton width="120px" height="14px" radius="4px" />
            <Skeleton width="70px" height="11px" radius="4px" />
          </div>
        </div>
        <Skeleton width="80px" height="20px" radius="12px" />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <Skeleton width="95%" height="14px" radius="4px" />
        <Skeleton width="75%" height="14px" radius="4px" />
      </div>
      <Skeleton width="100%" height="180px" radius="12px" style={{ marginTop: 4 }} />
    </div>
  )
}

export function FeedComunidade() {
  const [tipoFilter, setTipoFilter] = useState<TipoPostagem | undefined>(undefined)
  const { data, isLoading } = useFeedPostagens(tipoFilter)

  const mutacoesCriando = useMutationState({
    filters: { mutationKey: ['criar-postagem'], status: 'pending' },
    select: (mutation) => mutation.state,
  })
  const estaEnviandoPost = mutacoesCriando.length > 0

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

      {estaEnviandoPost && (
        <div className={styles.wrapperSkeletonPost}>
          <PostCardSkeleton />
        </div>
      )}

      <Transicao key={`${tipoFilter ?? 'tudo'}-${isLoading ? 'loading' : posts.length ? 'posts' : 'vazio'}`} modo="subir">
        {isLoading ? (
          <div className={styles.listaPosts}>
            <PostCardSkeleton />
            <PostCardSkeleton />
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
