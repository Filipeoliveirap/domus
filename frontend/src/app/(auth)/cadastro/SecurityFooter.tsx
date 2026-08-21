'use client'

import Link from 'next/link'
import { ShieldCheck } from 'lucide-react'
import styles from './SecurityFooter.module.css'

export function SecurityFooter() {
  return (
    <div className={styles.wrapper}>
      <div className={styles.box}>
        <ShieldCheck size={20} className={styles.icon} aria-hidden="true" />
        <p className={styles.text}>
          Seus dados estão protegidos pela nossa{' '}
          <Link href="/seguranca" className={styles.brand} target="_blank">
            Política de Segurança
          </Link>{' '}
          e <Link href="/privacidade" target="_blank">Política de Privacidade</Link>.
        </p>
      </div>
    </div>
  )
}