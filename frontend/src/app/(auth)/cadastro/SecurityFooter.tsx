'use client'

import { ShieldCheck } from 'lucide-react'
import styles from './SecurityFooter.module.css'

export function SecurityFooter() {
  return (
    <div className={styles.wrapper}>
      <div className={styles.box}>
        <ShieldCheck size={20} className={styles.icon} aria-hidden="true" />
        <p className={styles.text}>
          Seus dados estão protegidos sob nossa política de privacidade e conformidade
          eclesiástica. <strong className={styles.brand}>DOMUS Security Protocol v2.4</strong>
        </p>
      </div>
    </div>
  )
}