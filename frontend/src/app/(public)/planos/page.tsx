import TabelaPlanos from '@/components/landing/TabelaPlanos'
import styles from './planos.module.css'

export default function PlanosPage() {
  return (
    <main className={styles.container}>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Escolha o plano ideal para sua igreja</h1>
        <p className={styles.subtitulo}>
          Cadastre sua igreja e teste por 14 dias sem cobrança imediata. Altere ou cancele a qualquer momento.
        </p>
      </header>
      <TabelaPlanos />
    </main>
  )
}
