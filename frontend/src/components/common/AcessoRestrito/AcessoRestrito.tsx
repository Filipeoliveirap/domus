'use client'

import { useRouter } from 'next/navigation'
import { ShieldAlert, ArrowLeft, Home } from 'lucide-react'
import styles from './AcessoRestrito.module.css'

export function AcessoRestrito() {
  const router = useRouter()

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.iconeWrap}>
          <ShieldAlert size={36} strokeWidth={1.5} />
        </div>
        <span className={styles.selo}>Acesso negado</span>
        <h2 className={styles.titulo}>Acesso restrito</h2>
        <p className={styles.mensagem}>
          Você não tem permissão para acessar esta seção. Entre em contato com o
          administrador da sua igreja, se acreditar que isso é um engano.
        </p>
        <div className={styles.acoes}>
          <button className={styles.botaoSecundario} onClick={() => router.back()}>
            <ArrowLeft size={16} />
            Voltar
          </button>
          <button className={styles.botaoPrimario} onClick={() => router.push('/inicio')}>
            <Home size={16} />
            Ir para o início
          </button>
        </div>
      </div>
    </div>
  )
}