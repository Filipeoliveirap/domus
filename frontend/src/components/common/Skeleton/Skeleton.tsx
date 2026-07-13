import styles from "./Skeleton.module.css";

type SkeletonProps = {
  width?: string;
  height?: string;
  radius?: string;
  circle?: boolean;
  className?: string;
  style?: React.CSSProperties;
};

export function Skeleton({
  width = "100%",
  height = "14px",
  radius = "var(--radius-sm)",
  circle = false,
  className,
  style,
}: SkeletonProps) {
  return (
    <span
      className={`${styles.skeleton} ${className ?? ""}`}
      style={{
        width: circle ? height : width,
        height,
        borderRadius: circle ? "var(--radius-full)" : radius,
        ...style,
      }}
      aria-hidden="true"
    />
  );
}