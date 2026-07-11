import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import styles from './layout.module.css'

export default function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <>
      <Sidebar />
      <TopBar />
      <main className={styles.main}>
        {children}
      </main>
    </>
  )
}