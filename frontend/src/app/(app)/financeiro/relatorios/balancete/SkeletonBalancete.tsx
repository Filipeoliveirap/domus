import styles from './SkeletonBalancete.module.css'

/** Placeholder animado enquanto o balancete do ano carrega — substitui o texto
 *  "Carregando..." por um shimmer que já sugere o formato da tabela. */
export function SkeletonBalancete() {
  return (
    <div className={styles.wrap} aria-busy="true" aria-label="Carregando balancete">
      <div className={styles.barra} style={{ width: '38%' }} />
      {Array.from({ length: 9 }).map((_, i) => (
        <div key={i} className={styles.linha} />
      ))}
    </div>
  )
}
