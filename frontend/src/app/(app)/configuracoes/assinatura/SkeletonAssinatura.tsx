'use client'

import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './assinatura.module.css'

export function SkeletonAssinatura() {
  return (
    <div className={styles.container}>
      {/* Card Principal Skeleton usando exatamente a classe styles.card */}
      <div className={styles.card}>
        {/* Cabecalho Plano */}
        <div className={styles.cabecalhoPlano}>
          <div className={styles.planoInfo}>
            <Skeleton width="100px" height="22px" radius="9999px" />
            <Skeleton width="280px" height="26px" style={{ marginTop: 6 }} />
            <Skeleton width="200px" height="14px" style={{ marginTop: 4 }} />
          </div>
          <Skeleton width="86px" height="26px" radius="9999px" />
        </div>

        {/* Grid de Consumo */}
        <div className={styles.gridConsumo}>
          <div className={styles.boxConsumo}>
            <div className={styles.consumoTopo}>
              <Skeleton width="160px" height="12px" />
            </div>
            <Skeleton width="180px" height="24px" style={{ margin: '4px 0' }} />
            <div className={styles.barraProgressoBg}>
              <Skeleton width="100%" height="100%" radius="9999px" />
            </div>
          </div>

          <div className={styles.boxConsumo}>
            <div className={styles.consumoTopo}>
              <Skeleton width="160px" height="12px" />
            </div>
            <Skeleton width="180px" height="24px" style={{ margin: '4px 0' }} />
            <div className={styles.barraProgressoBg}>
              <Skeleton width="100%" height="100%" radius="9999px" />
            </div>
          </div>
        </div>

        {/* Botoes de Acao */}
        <div className={styles.botoesAcao}>
          <Skeleton width="140px" height="42px" radius="8px" />
          <Skeleton width="220px" height="42px" radius="8px" />
        </div>
      </div>
    </div>
  )
}
