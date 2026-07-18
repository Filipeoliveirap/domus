import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import styles from './DrawerDetalheMembro.module.css'

export function SkeletonDrawerMembro() {
  return (
    <>
      <div className={styles.conteudo}>
        {/* Topo: avatar + nome/email */}
        <div className={styles.topo}>
          <Skeleton width="56px" height="56px" radius="var(--radius-full)" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
            <Skeleton width="60%" height="18px" />
            <Skeleton width="45%" height="13px" />
          </div>
        </div>

        {/* Badge de status */}
        <Skeleton width="80px" height="22px" radius="var(--radius-full)" />

        {/* Quatro infoItems */}
        <div className={styles.infos}>
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className={styles.infoItem}>
              <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
                <Skeleton width="70px" height="10px" />
                <Skeleton width="50%" height="15px" />
              </div>
            </div>
          ))}
        </div>

        {/* Bloco de endereço */}
        <div className={styles.bloco}>
          <Skeleton width="80px" height="10px" />
          <Skeleton width="100%" height="13px" />
          <Skeleton width="65%" height="13px" />
        </div>
      </div>

      {/* Rodapé de auditoria */}
      <div className={styles.auditoria}>
        <Skeleton width="200px" height="13px" />
      </div>
    </>
  )
}
