import { Skeleton } from '@/components/common/Skeleton/Skeleton'

export function SkeletonVisitantes({ linhas = 8 }: { linhas?: number }) {
  return (
    <>
      {Array.from({ length: linhas }).map((_, i) => (
        <tr key={i}>
          <td>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Skeleton width="40px" height="40px" circle />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <Skeleton width="150px" height="14px" />
              </div>
            </div>
          </td>
          <td><Skeleton width="120px" height="14px" /></td>
          <td>
            <div style={{ display: 'flex', gap: 8 }}>
              <Skeleton width="64px" height="28px" radius="var(--radius-md)" />
              <Skeleton width="64px" height="28px" radius="var(--radius-md)" />
              <Skeleton width="64px" height="28px" radius="var(--radius-md)" />
            </div>
          </td>
          <td><Skeleton width="32px" height="32px" radius="var(--radius-md)" /></td>
        </tr>
      ))}
    </>
  )
}
