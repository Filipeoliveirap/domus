'use client'

import styles from './SanctuaryPanel.module.css'
import Image from 'next/image'

export function SanctuaryPanel() {
  return (
    <div className={styles.panel}>
      {/*
        Foto do Unsplash (licença livre para uso comercial, sem atribuição obrigatória).
        Servida em 1160x2000 (2x) para não borrar em tela retina.
        `priority`: é a maior imagem da tela de cadastro e aparece de cara — carregar
        preguiçosamente atrasaria o LCP.
      */}
      <Image
        src="/images/sanctuary.jpg"
        alt=""
        aria-hidden="true"
        className={styles.bgImage}
        width={580}
        height={1000}
        priority
      />

      {/* Escurece só onde o texto fica, preservando o resto da foto. */}
      <div className={styles.veu} aria-hidden="true" />

      <div className={styles.conteudo}>
        <p className={styles.frase}>
          Tecnologia a serviço da fé, organização a serviço da comunidade.
        </p>

        <div className={styles.divisor} aria-hidden="true" />

        {/*
          Antes esta assinatura estava queimada nos pixels do PNG exportado do Figma.
          Como texto real, ela é lida por leitor de tela, fica nítida em qualquer zoom
          e não exige nova exportação para mudar uma palavra.
        */}
        <p className={styles.marca}>
          DOMUS
          <span className={styles.assinatura}>Gestão Eclesiástica</span>
        </p>
      </div>
    </div>
  )
}
