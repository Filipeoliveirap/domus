import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import styles from './layout.module.css'
import { FaixaOffline } from '@/components/common/FaixaOffline/FaixaOffline'

export default function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <>
      <Sidebar />
      <TopBar />
      <FaixaOffline />
      <main className={styles.main}>
        {children}
      </main>
    </>
  )
}