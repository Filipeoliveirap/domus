import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./movimentacoes.module.css";

export function SkeletonMovimentacoes({ linhas = 8 }: { linhas?: number }) {
  return (
    <div className={styles.linhas}>
      {Array.from({ length: linhas }).map((_, i) => (
        <div key={i} className={styles.linha} style={{ cursor: "default" }}>
          {/* Descrição: duas linhas (texto + membro) */}
          <div className={styles.colDesc}>
            <Skeleton width="70%" height="14px" />
            <Skeleton width="45%" height="11px" />
          </div>
          {/* Categoria */}
          <div className={styles.colCat}>
            <Skeleton width="80%" height="13px" />
          </div>
          {/* Data */}
          <div className={styles.colData}>
            <Skeleton width="70px" height="13px" />
          </div>
          {/* Tipo (selo arredondado) */}
          <div className={styles.colTipo}>
            <Skeleton width="78px" height="22px" radius="var(--radius-full)" />
          </div>
          {/* Valor (alinhado à direita) */}
          <div className={styles.colValor}>
            <Skeleton width="80px" height="14px" style={{ marginLeft: "auto" }} />
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