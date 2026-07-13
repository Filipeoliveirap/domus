import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./SkeletonMembroForm.module.css";

export function SkeletonMembroForm() {
  return (
    <div className={styles.colunas}>
      {/* Coluna esquerda: 3 seções */}
      <div className={styles.coluna}>
        {/* Informações pessoais: header + grid de campos */}
        <div className={styles.secao}>
          <div className={styles.secaoHeader}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="200px" height="16px" />
          </div>
          {/* nome (largura cheia) */}
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <Skeleton width="130px" height="11px" />
            <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
          </div>
          {/* 4 campos em grid 2x2 */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>
            {[0, 1, 2, 3].map((i) => (
              <div key={i} style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <Skeleton width="80px" height="11px" />
                <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
              </div>
            ))}
          </div>
        </div>

        {/* Localização: header + textarea */}
        <div className={styles.secao}>
          <div className={styles.secaoHeader}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="120px" height="16px" />
          </div>
          <Skeleton width="90px" height="11px" />
          <Skeleton width="100%" height="100px" radius="var(--radius-md)" />
        </div>

        {/* Observações: header + textarea */}
        <div className={styles.secao}>
          <div className={styles.secaoHeader}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="140px" height="16px" />
          </div>
          <Skeleton width="120px" height="11px" />
          <Skeleton width="100%" height="100px" radius="var(--radius-md)" />
        </div>
      </div>

      {/* Coluna direita: seção da igreja + botões */}
      <div className={styles.coluna}>
        <div className={styles.secaoIgreja}>
          <div className={styles.secaoHeader}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="140px" height="16px" />
          </div>
          {/* Status cards: 3 opções empilhadas */}
          <Skeleton width="120px" height="11px" />
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} width="100%" height="64px" radius="var(--radius-md)" />
          ))}
          {/* Ministério */}
          <Skeleton width="90px" height="11px" style={{ marginTop: 4 }} />
          <Skeleton width="100%" height="46px" radius="var(--radius-md)" />
          {/* Info box */}
          <Skeleton width="100%" height="60px" radius="var(--radius-md)" />
        </div>
        <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
      </div>
    </div>
  );
}