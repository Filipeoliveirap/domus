import { redirect } from 'next/navigation'

// Enquanto não existe uma tela de entrada (landing), a raiz cai direto no login.
export default function Home() {
  redirect('/login')
}
