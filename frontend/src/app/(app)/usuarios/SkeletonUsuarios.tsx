import { Skeleton } from "@/components/common/Skeleton/Skeleton";
import styles from "./usuarios.module.css";

export function SkeletonUsuarios({ linhas = 8 }: { linhas?: number }) {
  return (
    <>
      {Array.from({ length: linhas }).map((_, i) => (
        <tr key={i}>
          <td>
            <div className={styles.celulaUsuario}>
              <Skeleton width="40px" height="40px" circle />
              <Skeleton width="140px" height="14px" />
            </div>
          </td>
          <td><Skeleton width="70%" height="14px" style={{ maxWidth: 220 }} /></td>
          <td><Skeleton width="72px" height="24px" radius="var(--radius-full)" /></td>
          <td><Skeleton width="56px" height="14px" /></td>
          <td><Skeleton width="90px" height="14px" /></td>
          <td className={styles.colunaAcoes}>
            <Skeleton width="32px" height="32px" radius="var(--radius-md)" style={{ marginLeft: "auto" }} />
          </td>
        </tr>
      ))}
    </>
  );
}