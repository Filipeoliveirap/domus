import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./Page.module.css";
import skel from "./SkeletonEventos.module.css";

export function SkeletonEventos({ cards = 8 }: { cards?: number }) {
  return (
    <div className={styles.grid}>
      {Array.from({ length: cards }).map((_, i) => (
        <div key={i} className={skel.card}>
          {/* Banner de 140px, com o selo sobreposto */}
          <div className={skel.imagem}>
            <Skeleton
              width="88px"
              height="24px"
              radius="var(--radius-full)"
              style={{ position: "absolute", top: 12, left: 12 }}
            />
          </div>

          {/* Corpo: dataBox à esquerda + info à direita */}
          <div className={skel.corpo}>
            <Skeleton width="52px" height="60px" radius="var(--radius-md)" />
            <div className={skel.info}>
              <Skeleton width="85%" height="16px" />
              <Skeleton width="100%" height="12px" />
              <Skeleton width="70%" height="12px" />
              <Skeleton width="55%" height="12px" style={{ marginTop: 4 }} />
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}