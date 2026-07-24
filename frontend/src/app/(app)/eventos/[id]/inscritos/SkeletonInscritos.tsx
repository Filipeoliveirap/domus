import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './inscritos.module.css'

export function SkeletonInscritos({ linhas = 6 }: { linhas?: number }) {
  return (
    <>
      <div className={styles.stats}>
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className={styles.statCard}>
            <Skeleton width="36px" height="36px" radius="var(--radius-md)" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', width: '100%' }}>
              <Skeleton width="40px" height="18px" />
              <Skeleton width="80px" height="11px" />
            </div>
          </div>
        ))}
      </div>

      <div className={styles.painel}>
        <div className={styles.tabelaHeader}>
          <span className={styles.colParticipante}>PARTICIPANTE</span>
          <span className={styles.colData}>DATA</span>
          <span className={styles.colInscritoPor}>INSCRITO POR</span>
          <span className={styles.colConvidados}>CONVIDADOS</span>
          <span className={styles.colAcoes}>AÇÕES</span>
        </div>

        <div className={styles.linhas}>
          {Array.from({ length: linhas }).map((_, i) => (
            <div key={i} className={styles.linha}>
              <div className={styles.colParticipante}>
                <Skeleton height="36px" circle />
                <Skeleton width="60%" height="14px" />
              </div>
              <div className={styles.colData}>
                <Skeleton width="70px" height="13px" />
              </div>
              <div className={styles.colInscritoPor} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Skeleton height="20px" circle />
                <Skeleton width="80px" height="13px" />
              </div>
              <div className={styles.colConvidados}>
                <Skeleton width="20px" height="13px" />
              </div>
              <div className={styles.colAcoes}>
                <Skeleton width="70px" height="26px" radius="var(--radius-md)" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  )
}
