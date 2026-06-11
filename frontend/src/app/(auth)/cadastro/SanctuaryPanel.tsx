'use client'

import styles from './SanctuaryPanel.module.css'
import Image from 'next/image'

export function SanctuaryPanel() {
  return (
    
    <div className={styles.panel}>
      {/* Imagem de fundo — substitua o src pela sua imagem baixada do Figma */}
      <Image
        src="/images/sactuaryimage.png"
        alt=""
        aria-hidden="true"
        className={styles.bgImage}
        width={600}
        height={800}
      />

    </div> 
  )
}