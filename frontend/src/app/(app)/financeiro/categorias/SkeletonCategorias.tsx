import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./categoria.module.css";

export function SkeletonCategorias({ linhas = 8 }: { linhas?: number }) {
  return (
    <div className={styles.linhas}>
      {Array.from({ length: linhas }).map((_, i) => (
        <div key={i} className={styles.linha} style={{ cursor: "default" }}>
          {/* Nome: iconeBox quadrado + texto */}
          <div className={styles.colNome}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="55%" height="14px" style={{ maxWidth: 200 }} />
          </div>
          {/* Tipo: selo arredondado */}
          <div className={styles.colTipo}>
            <Skeleton width="96px" height="26px" radius="var(--radius-full)" />
          </div>
          {/* Ações */}
          <div className={styles.colAcoes}>
            <Skeleton width="28px" height="28px" radius="var(--radius-md)" />
          </div>
        </div>
      ))}
    </div>
  );
}