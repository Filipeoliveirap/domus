import { RoleGuard } from '@/components/auth/RoleGuard'

export default function UsuariosLayout({ children }: { children: React.ReactNode }) {
  return <RoleGuard roles={['ADMIN_IGREJA']}>{children}</RoleGuard>
}