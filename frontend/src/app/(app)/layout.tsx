import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import styles from './layout.module.css'
import { FaixaOffline } from '@/components/common/FaixaOffline/FaixaOffline'
import { AuthGuard } from '@/components/auth/AuthGuard'

export default function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <AuthGuard>
      <FaixaOffline />
      <Sidebar />
      <TopBar />
      <main className={styles.main}>
        {children}
      </main>
    </AuthGuard>
  )
}