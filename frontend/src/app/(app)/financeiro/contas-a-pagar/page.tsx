'use client'

import { Suspense } from 'react'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { ContasAPagarPage } from '@/components/financeiro/contas-a-pagar/ContasAPagarPage'
import styles from './contas-a-pagar.module.css'

export default function Page() {
  const { role } = useAuthStore()

  if (!podeVerFinanceiro(role)) {
    return <AcessoRestrito />
  }

  return (
    <Suspense
      fallback={
        <div className={styles.suspense}>
          <Skeleton width="100%" height="60px" />
          <Skeleton width="100%" height="100px" />
          <Skeleton width="100%" height="200px" />
        </div>
      }
    >
      <ContasAPagarPage />
    </Suspense>
  )
}

