import { redirect } from 'next/navigation'

/** /configuracoes sozinho não é uma tela — cai na primeira aba. */
export default function ConfiguracoesPage() {
  redirect('/configuracoes/igreja')
}
