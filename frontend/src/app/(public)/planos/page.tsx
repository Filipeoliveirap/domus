'use client'

import { Suspense } from 'react'
import { useSearchParams } from 'next/navigation'
import TabelaPlanos from '@/components/landing/TabelaPlanos'
import styles from './planos.module.css'

function CabecalhoPlanos() {
  const searchParams = useSearchParams()
  const veioDeTrial = searchParams?.get('origem') === 'trial' || searchParams?.get('trial') === 'true'

  return (
    <header className={styles.cabecalho}>
      <h1 className={styles.titulo}>
        {veioDeTrial
          ? 'Escolha um plano antes de começar seu teste grátis'
          : 'Escolha o plano ideal para sua igreja'}
      </h1>
      <p className={styles.subtitulo}>
        {veioDeTrial
          ? 'Você terá 14 dias de acesso completo sem nenhuma cobrança no momento do cadastro. Altere ou cancele a qualquer momento com um clique.'
          : 'Cadastre sua igreja e teste por 14 dias sem cobrança imediata. Altere ou cancele a qualquer momento.'}
      </p>
    </header>
  )
}

export default function PlanosPage() {
  return (
    <main className={styles.container}>
      <Suspense fallback={
        <header className={styles.cabecalho}>
          <h1 className={styles.titulo}>Escolha o plano ideal para sua igreja</h1>
          <p className={styles.subtitulo}>
            Cadastre sua igreja e teste por 14 dias sem cobrança imediata. Altere ou cancele a qualquer momento.
          </p>
        </header>
      }>
        <CabecalhoPlanos />
      </Suspense>
      <TabelaPlanos />
    </main>
  )
}
