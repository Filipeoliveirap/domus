'use client'

import { useRegistrarIgreja } from '../../../hooks/auth/UseRegistrarIgreja'
import { CadastroShell } from './CadastroShell'

export default function CadastroPage() {
  const hook = useRegistrarIgreja()
  return <CadastroShell {...hook} />
}
