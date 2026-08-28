import { Loader } from '@/components/common/Loader/Loader'
import { AvisoRotaCarregando } from './AvisoRotaCarregando'
import styles from './loading.module.css'

export default function AppLoading() {
  return (
    <div className={styles.wrapper} data-app-loading>
      <AvisoRotaCarregando />
      <Loader variant="circular" size="lg" />
    </div>
  )
}
