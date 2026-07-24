'use client'

import { Skeleton } from '@/components/common/Skeleton/Skeleton'

/**
 * Skeleton da página /configuracoes/igrejas-vinculadas.
 * Layout de card único com header, ícone, botões e conteúdo.
 */
export function SkeletonIgrejasVinculadas() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, minWidth: 0 }}>
      {/* Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Skeleton width="60px" height="12px" />
        <Skeleton width="80px" height="12px" />
      </div>
      <Skeleton width="240px" height="28px" />

      {/* Card principal */}
      <div style={{
        display: 'flex', flexDirection: 'column', gap: 24,
        background: 'var(--color-bg-white)', border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)', padding: 28, minWidth: 0,
      }}>
        {/* Header do card */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Skeleton width="48px" height="48px" radius="var(--radius-md)" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Skeleton width="200px" height="18px" />
              <Skeleton width="160px" height="14px" />
            </div>
          </div>
          <Skeleton width="140px" height="40px" radius="var(--radius-md)" />
        </div>

        {/* Conteúdo — blocos de seção */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <Skeleton width="120px" height="11px" style={{ marginBottom: 8 }} />
          <Skeleton width="100%" height="1px" />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Linhas de igrejas vinculadas */}
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '12px 0',
            }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <Skeleton width="180px" height="14px" />
                <Skeleton width="120px" height="12px" />
              </div>
              <Skeleton width="80px" height="32px" radius="var(--radius-md)" />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
