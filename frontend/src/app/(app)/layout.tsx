import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import styles from './layout.module.css'
import { FaixaOffline } from '@/components/common/FaixaOffline/FaixaOffline'
import { BannerExclusaoAgendada } from '@/components/common/BannerExclusaoAgendada/BannerExclusaoAgendada'
import { AuthGuard } from '@/components/auth/AuthGuard'
import { NavProgress } from '@/components/layout/NavProgress/NavProgress'
import { TransicaoRota } from '@/components/common/Transicao/TransicaoRota'
import { PortalBoasVindas } from '@/components/common/PortalBoasVindas/PortalBoasVindas'

export default function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <AuthGuard>
      <NavProgress />
      <PortalBoasVindas />
      <FaixaOffline />
      <BannerExclusaoAgendada />
      <Sidebar />
      <TopBar />
      <main className={styles.main}>
        <TransicaoRota>{children}</TransicaoRota>
      </main>
    </AuthGuard>
  )
}