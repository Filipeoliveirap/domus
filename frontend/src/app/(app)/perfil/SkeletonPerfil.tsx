'use client'

import { Skeleton } from '@/components/common/Skeleton/Skeleton'

/**
 * Skeleton da página /perfil (Meu Perfil). Mantém o mesmo layout do formulário real
 * para evitar CLS — foto centralizada, aviso, seções com grid de campos, botão.
 */
export function SkeletonPerfil() {
  return (
    <div style={{ maxWidth: 760, margin: '0 auto', padding: 24, display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Cabeçalho */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <Skeleton width="160px" height="28px" />
        <Skeleton width="280px" height="16px" />
      </div>

      {/* Card do formulário */}
      <div style={{
        display: 'flex', flexDirection: 'column', gap: 24,
        background: 'var(--color-bg-white)', border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)', padding: 28, minWidth: 0,
      }}>
        {/* Foto */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 4 }}>
          <Skeleton width="96px" height="96px" circle />
        </div>

        {/* Aviso */}
        <Skeleton width="100%" height="52px" radius="var(--radius-md)" />

        {/* Info Pessoais */}
        <SeçãoSkeleton icone tabelaCampos />

        {/* Localização */}
        <SeçãoSkeleton icone />

        {/* Dados da igreja + botão */}
        <SeçãoSkeleton icone>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 16 }}>
            <Skeleton width="70px" height="11px" />
            <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
            <Skeleton width="70px" height="11px" />
            <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
          </div>
          <Skeleton width="100%" height="46px" radius="var(--radius-md)" style={{ marginTop: 12 }} />
        </SeçãoSkeleton>

        <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
      </div>
    </div>
  )
}

function SeçãoSkeleton({ icone, tabelaCampos, children }: { icone?: boolean; tabelaCampos?: boolean; children?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, minWidth: 0 }}>
      {icone && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
          <Skeleton width="180px" height="16px" />
        </div>
      )}
      {tabelaCampos && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, minWidth: 0 }}>
          <div style={{ gridColumn: '1 / -1' }}>
            <Skeleton width="70px" height="11px" style={{ marginBottom: 6 }} />
            <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
          </div>
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i}>
              <Skeleton width="70px" height="11px" style={{ marginBottom: 6 }} />
              <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
            </div>
          ))}
        </div>
      )}
      {children}
    </div>
  )
}
