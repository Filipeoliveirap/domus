'use client'

import { use, Suspense } from 'react'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useVisitante } from '@/hooks/visitante/useVisitante'
import { useVisitanteForm } from '@/hooks/visitante/useVisitanteForm'
import { VisitanteForm } from '@/components/module/visitantes/VisitanteForm'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './page.module.css'

function EditarVisitanteConteudo({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const { data: visitante, isPending, isError, refetch } = useVisitante(id)
  const form = useVisitanteForm({ visitanteId: id, visitanteInicial: visitante })

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/pessoas/visitantes" className={styles.breadcrumbLink}>Visitantes</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Editar</span>
      </nav>

      {isPending ? (
        <div className={styles.skeletonWrap}>
          <Skeleton width="200px" height="28px" />
          <Skeleton width="400px" height="18px" />
          <div style={{ marginTop: 24 }}>
            <Skeleton width="100%" height="400px" radius="var(--radius-lg)" />
          </div>
        </div>
      ) : isError || !visitante ? (
        <div className={styles.erroWrap}>
          <EstadoErro
            titulo="Não foi possível carregar o visitante"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        </div>
      ) : (
        <>
          <h1 className={styles.titulo}>Editar Visitante</h1>
          <p className={styles.subtitulo}>Atualize os dados de acompanhamento do visitante.</p>
          <VisitanteForm {...form} />
        </>
      )}
    </div>
  )
}

export default function EditarVisitantePage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={
      <div className={styles.pagina}>
        <div className={styles.skeletonWrap}>
          <Skeleton width="200px" height="28px" />
          <Skeleton width="100%" height="400px" radius="var(--radius-lg)" />
        </div>
      </div>
    }>
      <EditarVisitanteConteudo params={params} />
    </Suspense>
  )
}
