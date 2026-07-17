import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./SkeletonMovimentacaoForm.module.css";

export function SkeletonMovimentacaoForm() {
  return (
    <div className={styles.colunas}>
      {/* Coluna esquerda: seção de campos */}
      <div className={styles.coluna}>
        <div className={styles.secao}>
          <div className={styles.secaoHeader}>
            <Skeleton width="40px" height="40px" radius="var(--radius-md)" />
            <Skeleton width="200px" height="16px" />
          </div>

          {/* Seletor de tipo: dois botões grandes */}
          <Skeleton width="90px" height="11px" />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Skeleton width="100%" height="92px" radius="var(--radius-md)" />
            <Skeleton width="100%" height="92px" radius="var(--radius-md)" />
          </div>

          {/* Valor (destacado, 56px) */}
          <Skeleton width="60px" height="11px" style={{ marginTop: 8 }} />
          <Skeleton width="100%" height="56px" radius="var(--radius-md)" />

          {/* Categoria + Data lado a lado */}
          <div style={{ display: "flex", gap: 16, marginTop: 8 }}>
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 6 }}>
              <Skeleton width="80px" height="11px" />
              <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
            </div>
            <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 6 }}>
              <Skeleton width="50px" height="11px" />
              <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
            </div>
          </div>

          {/* Membro */}
          <Skeleton width="110px" height="11px" style={{ marginTop: 8 }} />
          <Skeleton width="100%" height="48px" radius="var(--radius-md)" />

          {/* Descrição */}
          <Skeleton width="90px" height="11px" style={{ marginTop: 8 }} />
          <Skeleton width="100%" height="100px" radius="var(--radius-md)" />
        </div>
      </div>

      {/* Coluna direita: resumo + botões */}
      <div className={styles.coluna}>
        <div className={styles.resumo}>
          <Skeleton width="160px" height="16px" />
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <Skeleton width="40px" height="13px" />
            <Skeleton width="70px" height="13px" />
          </div>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <Skeleton width="70px" height="13px" />
            <Skeleton width="90px" height="13px" />
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", paddingTop: 16, borderTop: "1px solid rgba(19,27,46,0.1)" }}>
            <Skeleton width="50px" height="15px" />
            <Skeleton width="100px" height="22px" />
          </div>
          <Skeleton width="100%" height="60px" radius="var(--radius-md)" />
        </div>
        <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
      </div>
    </div>
  );
}