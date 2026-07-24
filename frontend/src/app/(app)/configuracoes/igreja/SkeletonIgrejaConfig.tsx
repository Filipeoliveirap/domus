'use client'

import { Skeleton } from '@/components/common/Skeleton/Skeleton'

/**
 * Skeleton da página /configuracoes/igreja (Dados da Igreja).
 * Layout de duas colunas: aside institucional + formulário com logo e grade de campos.
 */
export function SkeletonIgrejaConfig() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, minWidth: 0 }}>
      {/* Breadcrumb */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Skeleton width="60px" height="12px" />
        <Skeleton width="80px" height="12px" />
      </div>
      <Skeleton width="200px" height="28px" />

      {/* Colunas */}
      <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
        {/* Aside institucional */}
        <div style={{
          flex: '0 0 280px', display: 'flex', flexDirection: 'column', gap: 20,
          background: 'var(--color-bg-white)', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)', padding: 28, minWidth: 0,
        }}>
          <Skeleton width="56px" height="56px" radius="var(--radius-md)" />
          <Skeleton width="160px" height="18px" />
          <Skeleton width="240px" height="14px" />
          <Skeleton width="240px" height="14px" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 8 }}>
            {[0, 1].map((i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                <Skeleton width="16px" height="16px" />
                <Skeleton width="220px" height="28px" radius="var(--radius-sm)" />
              </div>
            ))}
          </div>
        </div>

        {/* Formulário */}
        <div style={{
          flex: 1, display: 'flex', flexDirection: 'column', gap: 20,
          background: 'var(--color-bg-white)', border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)', padding: 28, minWidth: 0,
        }}>
          {/* Logo */}
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 4 }}>
            <Skeleton width="88px" height="88px" circle />
          </div>

          {/* Grade de campos */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} style={i % 2 === 0 ? { gridColumn: '1 / -1' } : undefined}>
                <Skeleton width="80px" height="11px" style={{ marginBottom: 6 }} />
                <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
              </div>
            ))}
          </div>

          {/* Endereço */}
          <Skeleton width="100%" height="1px" style={{ margin: '8px 0' }} />
          <Skeleton width="100px" height="16px" style={{ marginBottom: 4 }} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
            {Array.from({ length: 7 }).map((_, i) => (
              <div key={i} style={i === 0 || i === 1 ? { gridColumn: '1 / -1' } : undefined}>
                <Skeleton width="70px" height="11px" style={{ marginBottom: 6 }} />
                <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
              </div>
            ))}
          </div>

          {/* Botões */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 8 }}>
            <Skeleton width="160px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="160px" height="40px" radius="var(--radius-md)" />
          </div>
        </div>
      </div>

      {/* Cards de rodapé */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
        {[0, 1].map((i) => (
          <div key={i} style={{
            background: 'var(--color-bg-white)', border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)', padding: 20, display: 'flex', flexDirection: 'column', gap: 12,
          }}>
            <Skeleton width="180px" height="16px" />
            {i === 0 ? (
              <>
                {[0, 1].map((j) => (
                  <div key={j} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Skeleton width="120px" height="14px" />
                    <Skeleton width="80px" height="14px" />
                  </div>
                ))}
              </>
            ) : (
              <>
                <Skeleton width="100%" height="8px" radius="var(--radius-full)" />
                <Skeleton width="280px" height="14px" />
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
