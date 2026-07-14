import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import cards from "./CardsResumo.module.css";
import barra from "./BarraProporcao.module.css";
import destaques from "./Destaques.module.css";
import breakdown from "./BreakdownCategoria.module.css";
import grafico from "./GraficoEvolucao.module.css";

export function SkeletonCardsResumo() {
  return (
    <div className={cards.grid}>
      {[0, 1, 2].map((i) => (
        <div key={i} className={cards.card}>
          <div className={cards.cardTopo}>
            <Skeleton width="90px" height="12px" />
            <Skeleton width="36px" height="36px" radius="var(--radius-md)" />
          </div>
          <Skeleton width="60%" height="28px" />
          <Skeleton width="70%" height="13px" />
        </div>
      ))}
    </div>
  );
}

export function SkeletonBarraProporcao() {
  return (
    <div className={barra.painel}>
      <div className={barra.header}>
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <Skeleton width="220px" height="15px" />
          <Skeleton width="300px" height="12px" />
        </div>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 6 }}>
          <Skeleton width="60px" height="22px" />
          <Skeleton width="90px" height="11px" />
        </div>
      </div>
      <Skeleton width="100%" height="12px" radius="var(--radius-full)" />
      <div style={{ display: "flex", gap: 24 }}>
        <Skeleton width="140px" height="13px" />
        <Skeleton width="140px" height="13px" />
      </div>
    </div>
  );
}

export function SkeletonDestaques() {
  return (
    <div className={destaques.grid}>
      {[0, 1, 2].map((i) => (
        <div key={i} className={destaques.card}>
          <Skeleton width="44px" height="44px" radius="var(--radius-md)" />
          <div style={{ display: "flex", flexDirection: "column", gap: 6, flex: 1 }}>
            <Skeleton width="80%" height="11px" />
            <Skeleton width="55%" height="16px" />
            <Skeleton width="40%" height="12px" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonBreakdownCategoria() {
  return (
    <div className={breakdown.grid}>
      {[0, 1].map((col) => (
        <div key={col} className={breakdown.coluna}>
          <div className={breakdown.colunaHeader}>
            <Skeleton width="32px" height="32px" radius="var(--radius-md)" />
            <Skeleton width="180px" height="15px" />
          </div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            {[0, 1, 2, 3].map((linha) => (
              <div
                key={linha}
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr auto 60px",
                  gap: 12,
                  alignItems: "center",
                  padding: "14px 0",
                  borderBottom: "1px solid var(--color-border-subtle)",
                }}
              >
                <Skeleton width="60%" height="14px" />
                <Skeleton width="70px" height="14px" />
                <Skeleton width="40px" height="18px" radius="var(--radius-full)" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonGraficoEvolucao() {
  const alturas = ["55%", "80%", "40%", "95%", "65%", "75%"];
  return (
    <div className={grafico.painel}>
      <Skeleton width="160px" height="16px" />
      <div
        style={{
          display: "flex",
          alignItems: "flex-end",
          justifyContent: "space-around",
          gap: 24,
          height: 300,
          paddingTop: 12,
        }}
      >
        {alturas.map((h, i) => (
          <div
            key={i}
            style={{ display: "flex", gap: 6, alignItems: "flex-end", height: "100%", flex: 1 }}
          >
            <Skeleton width="50%" height={h} radius="var(--radius-sm)" />
            <Skeleton
              width="50%"
              height={`${parseInt(h) * 0.6}%`}
              radius="var(--radius-sm)"
            />
          </div>
        ))}
      </div>
    </div>
  );
}