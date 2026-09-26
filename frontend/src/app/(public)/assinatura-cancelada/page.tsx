'use client'

import React from 'react'
import Link from 'next/link'
import styles from './assinaturaCancelada.module.css'

export default function AssinaturaCanceladaPage() {
  return (
    <div className={styles.pagina}>
      <div className={styles.card}>
        <div className={styles.iconeBox}>
          ⚠️
        </div>

        <div>
          <h1 className={styles.titulo}>Assinatura Cancelada</h1>
          <p className={styles.descricao}>
            A assinatura do plano Domus da sua igreja foi cancelada. O acesso aos módulos e recursos foi temporariamente interrompido.
          </p>
        </div>

        <div className={styles.caixaInfo}>
          <p className={styles.caixaInfoTitulo}>Como reativar sua conta?</p>
          Como administrador, você pode reativar seu plano imediatamente escolhendo uma opção de pagamento abaixo. Todos os dados da sua igreja foram preservados.
        </div>

        <div className={styles.botoesGroup}>
          <Link href="/planos" className={styles.btnPrimary}>
            Reativar Assinatura Agora
          </Link>

          <Link href="/login" className={styles.btnSecondary}>
            Voltar para o Login
          </Link>
        </div>
      </div>
    </div>
  )
}
