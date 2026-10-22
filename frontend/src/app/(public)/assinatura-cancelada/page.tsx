'use client'

import React from 'react'
import Link from 'next/link'
import { ShieldAlert, ArrowRight, ArrowLeft } from 'lucide-react'
import styles from './assinaturaCancelada.module.css'

export default function AssinaturaCanceladaPage() {
  return (
    <div className={styles.pagina}>
      <div className={styles.card}>
        <div className={styles.iconeWrap}>
          <ShieldAlert size={36} strokeWidth={1.75} aria-hidden="true" />
        </div>

        <span className={styles.selo}>Conta inativa</span>

        <h1 className={styles.titulo}>Assinatura Cancelada</h1>
        <p className={styles.descricao}>
          A assinatura do plano Domus da sua igreja foi cancelada. O acesso aos módulos e recursos foi temporariamente interrompido.
        </p>

        <div className={styles.caixaReativacao}>
          <p className={styles.caixaTitulo}>Como reativar sua conta?</p>
          Como administrador, você pode reativar seu plano imediatamente escolhendo uma opção de assinatura. Todos os dados da sua igreja continuam preservados com segurança.
        </div>

        <div className={styles.acoes}>
          <Link href="/planos" className={styles.btnPrimary}>
            <span>Reativar Assinatura Agora</span>
            <ArrowRight size={16} />
          </Link>

          <Link href="/login" className={styles.btnSecondary}>
            <ArrowLeft size={16} />
            <span>Voltar para o Login</span>
          </Link>
        </div>
      </div>
    </div>
  )
}
