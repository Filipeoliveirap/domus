import { Loader } from '@/components/common/Loader/Loader'
import styles from './PagamentoEvento.module.css'

/**
 * Mostrado enquanto o segmento da rota de checkout carrega — a navegação vem de dentro do
 * app shell (com sidebar/drawer) pra esta rota full-screen, então sem isto haveria um flash
 * branco. Esqueleto do topo + spinner, no mesmo enquadramento da página real.
 */
export default function CarregandoPagamento() {
  return (
    <div className={styles.pagina}>
      <header className={styles.topo}>
        <span className={styles.esqueletoTopo} aria-hidden="true" />
        <div className={styles.esqueletoLinhas} aria-hidden="true">
          <span className={styles.esqueletoLinha} />
          <span className={styles.esqueletoLinha} />
        </div>
      </header>
      <div className={styles.esqueletoCentro} role="status" aria-live="polite">
        <Loader variant="circular" size="lg" />
        <span>Abrindo o pagamento…</span>
      </div>
    </div>
  )
}
