import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import styles from './layout.module.css'
import { FaixaOffline } from '@/components/common/FaixaOffline/FaixaOffline'
import { BannerExclusaoAgendada } from '@/components/common/BannerExclusaoAgendada/BannerExclusaoAgendada'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { NavProgress } from '@/components/layout/NavProgress/NavProgress'

export default function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <AuthGuard>
      <NavProgress />
      <FaixaOffline />
      <BannerExclusaoAgendada />
      <Sidebar />
      <TopBar />
      <main className={styles.main}>
        {children}
      </main>
    </AuthGuard>
  )
}