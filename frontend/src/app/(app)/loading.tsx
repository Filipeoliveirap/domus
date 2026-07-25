import styles from './loading.module.css'

export default function AppLoading() {
  return (
    <div className={styles.wrapper}>
      <div className={styles.spinner} />
    </div>
  )
}
