import { Sidebar } from '@/components/layout/Sidebar'
import styles from './layout.module.css'

export default function AppLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <>
      <Sidebar />
      <main className={styles.main}>
        {children}
      </main>
    </>
  )
}