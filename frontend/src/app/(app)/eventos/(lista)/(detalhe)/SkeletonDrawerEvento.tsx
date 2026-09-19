import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./DrawerDetalheEvento.module.css";

/** Espelha a ordem atual do drawer real: header → infos → presença → descrição →
 *  imagem (banner ficou por último, ver DrawerDetalheEvento) → ações. */
export function SkeletonDrawerEvento() {
  return (
    <div className={styles.conteudo}>
      {/* Header: selo + data + título */}
      <header className={styles.header}>
        <Skeleton width="72px" height="20px" radius="var(--radius-full)" />
        <Skeleton width="140px" height="13px" />
        <Skeleton width="80%" height="26px" />
      </header>

      {/* Infos — horário sempre aparece; local/responsável/prazo/preço variam por evento,
          então 3 placeholders cobrem a média sem prometer um número exato. */}
      <div className={styles.infos}>
        {[0, 1, 2].map((i) => (
          <div key={i} className={styles.infoItem}>
            <Skeleton width="44px" height="44px" radius="var(--radius-md)" />
            <div style={{ display: "flex", flexDirection: "column", gap: 6, flex: 1 }}>
              <Skeleton width="60px" height="10px" />
              <Skeleton width="55%" height="15px" />
            </div>
          </div>
        ))}
      </div>

      {/* Presença: pilha de avatares + texto + contador de vagas */}
      <div className={styles.presenca}>
        <div className={styles.presencaPessoas}>
          <div className={styles.pilhaAvatares}>
            {[0, 1, 2].map((i) => (
              <span key={i} className={styles.avatarPresenca} style={{ "--i": i } as React.CSSProperties}>
                <Skeleton width="100%" height="100%" radius="var(--radius-full)" />
              </span>
            ))}
          </div>
          <Skeleton width="60%" height="14px" />
        </div>
        <Skeleton width="45%" height="12px" style={{ marginTop: 8 }} />
      </div>

      {/* Descrição */}
      <div className={styles.descricaoBloco}>
        <Skeleton width="90%" height="14px" />
        <Skeleton width="65%" height="14px" style={{ marginTop: 6 }} />
      </div>

      {/* Banner (200px, agora perto do fim do conteúdo) */}
      <Skeleton width="100%" height="200px" radius="var(--radius-lg)" />

      {/* Ações: confirmar presença + secundária */}
      <div className={styles.acoesInscricao}>
        <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
        <Skeleton width="100%" height="48px" radius="var(--radius-md)" />
      </div>
    </div>
  );
}