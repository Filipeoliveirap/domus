import { Loader } from '@/components/common/Loader/Loader'
import styles from './loading.module.css'

export default function AppLoading() {
  return (
    <div className={styles.wrapper}>
      <Loader variant="circular" size="lg" />
    </div>
  )
}
