import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./page.module.css";

export function SkeletonMembros({
  linhas = 8,
  podeGerenciar = false,
}: {
  linhas?: number;
  podeGerenciar?: boolean;
}) {
  return (
    <>
      {Array.from({ length: linhas }).map((_, i) => (
        <tr key={i}>
          <td>
            <div className={styles.celulaMembro}>
              <Skeleton width="40px" height="40px" circle />
              <div className={styles.membroInfo}>
                <Skeleton width="150px" height="14px" />
                <Skeleton width="190px" height="12px" />
              </div>
            </div>
          </td>
          <td><Skeleton width="120px" height="14px" /></td>
          <td><Skeleton width="80px" height="24px" radius="var(--radius-full)" /></td>
          <td><Skeleton width="90px" height="14px" /></td>
          {podeGerenciar && (
            <td className={styles.colunaAcoes}>
              <Skeleton width="32px" height="32px" radius="var(--radius-md)" style={{ marginLeft: "auto" }} />
            </td>
          )}
        </tr>
      ))}
    </>
  );
}