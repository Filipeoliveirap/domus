import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./DrawerDetalheMovimentacao.module.css";

export function SkeletonDrawerMovimentacao() {
  return (
    <>
      <div className={styles.conteudo}>
        {/* Topo: selo + valor grande */}
        <div className={styles.topo}>
          <Skeleton width="90px" height="24px" radius="var(--radius-full)" />
          <Skeleton width="160px" height="30px" />
        </div>

        {/* Três infoItems */}
        <div className={styles.infos}>
          {[0, 1, 2].map((i) => (
            <div key={i} className={styles.infoItem}>
              <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
              <div style={{ display: "flex", flexDirection: "column", gap: 6, flex: 1 }}>
                <Skeleton width="70px" height="10px" />
                <Skeleton width="50%" height="15px" />
              </div>
            </div>
          ))}
        </div>

        {/* Caixa de descrição */}
        <div className={styles.descricaoBloco}>
          <Skeleton width="80px" height="10px" />
          <Skeleton width="100%" height="13px" />
          <Skeleton width="65%" height="13px" />
        </div>
      </div>

      {/* Rodapé de auditoria */}
      <div className={styles.auditoria}>
        <Skeleton width="220px" height="13px" />
      </div>
    </>
  );
}