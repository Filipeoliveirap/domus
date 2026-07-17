import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./SkeletonEventoForm.module.css";

export function SkeletonEventoForm() {
  return (
    <div className={styles.colunas}>
      {/* Coluna esquerda: duas caixas de seção */}
      <div className={styles.coluna}>
        {[0, 1].map((s) => (
          <div key={s} className={styles.secao}>
            <div className={styles.secaoHeader}>
              <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
              <Skeleton width="180px" height="16px" />
            </div>
            <Skeleton width="90px" height="11px" />
            <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
            {s === 0 && (
              <>
                <Skeleton width="90px" height="11px" style={{ marginTop: 8 }} />
                <Skeleton width="100%" height="110px" radius="var(--radius-md)" />
              </>
            )}
          </div>
        ))}
      </div>

      {/* Coluna direita: caixa de data + botão */}
      <div className={styles.coluna}>
        <div className={styles.secaoData}>
          <div className={styles.secaoHeader}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="140px" height="16px" />
          </div>
          {[0, 1].map((i) => (
            <div key={i} style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <Skeleton width="70px" height="11px" />
              <div style={{ display: "flex", gap: 10 }}>
                <Skeleton width="66%" height="48px" radius="var(--radius-md)" />
                <Skeleton width="34%" height="48px" radius="var(--radius-md)" />
              </div>
            </div>
          ))}
          <Skeleton width="100%" height="90px" radius="var(--radius-md)" />
        </div>
        <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
      </div>
    </div>
  );
}