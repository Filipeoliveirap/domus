'use client'

import { useRouter } from 'next/navigation'
import Image from 'next/image'
import { ArrowLeft, Home } from 'lucide-react'
import styles from './not-found.module.css'

export default function NotFound() {
  const router = useRouter()

  return (
    <div className={styles.container}>
      <div className={styles.conteudo}>
        <Image
          src="/imagens/logo.png"
          alt="Domus"
          width={140}
          height={48}
          className={styles.logo}
          priority
        />

        <p className={styles.mensagem}>
          Página indisponível. Lamentamos o transtorno.<br />
            Você pode voltar para a página anterior ou ir para a página inicial.
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