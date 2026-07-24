import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './relatorio.module.css'

export function SkeletonRelatorioGeral({ linhas = 5 }: { linhas?: number }) {
  return (
    <>
      <div className={styles.resumoGrade}>
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className={styles.resumoCard}>
            <Skeleton width="60px" height="26px" />
            <Skeleton width="100px" height="12px" />
          </div>
        ))}
      </div>

      <section className={styles.secao}>
        <Skeleton width="220px" height="15px" />
        <Skeleton width="100%" height="220px" radius="var(--radius-md)" />
      </section>

      <section className={styles.secao}>
        <Skeleton width="140px" height="15px" />
        <div className={styles.listaEventos}>
          {Array.from({ length: linhas }).map((_, i) => (
            <div key={i} className={styles.linhaEvento}>
              <div className={styles.linhaEventoInfo}>
                <Skeleton width="60%" height="14px" />
                <Skeleton width="80px" height="11px" />
              </div>
              <Skeleton width="90px" height="14px" style={{ marginLeft: 'auto' }} />
              <div className={styles.linhaEventoVariacoes}>
                <Skeleton width="70px" height="24px" radius="var(--radius-full)" />
                <Skeleton width="90px" height="24px" radius="var(--radius-full)" />
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
