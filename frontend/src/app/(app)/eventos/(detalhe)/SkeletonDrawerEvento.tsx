import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./DrawerDetalheEvento.module.css";

export function SkeletonDrawerEvento() {
  return (
    <div className={styles.conteudo}>
      {/* Header: selo + data + título */}
      <header className={styles.header}>
        <Skeleton width="72px" height="20px" radius="var(--radius-full)" />
        <Skeleton width="140px" height="13px" />
        <Skeleton width="80%" height="26px" />
      </header>

      {/* Dois infoItems (horário e local) */}
      <div className={styles.infos}>
        {[0, 1].map((i) => (
          <div key={i} className={styles.infoItem}>
            <Skeleton width="44px" height="44px" radius="var(--radius-md)" />
            <div style={{ display: "flex", flexDirection: "column", gap: 6, flex: 1 }}>
              <Skeleton width="60px" height="10px" />
              <Skeleton width="55%" height="15px" />
            </div>
          </div>
        ))}
      </div>

      {/* Bloco de imagem (200px) */}
      <Skeleton width="100%" height="200px" radius="var(--radius-lg)" />
    </div>
  );
}