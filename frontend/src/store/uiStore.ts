import { create } from 'zustand'

// Estado de UI global e efêmero (não persiste).
interface UiState {
  navAberta: boolean
  abrirNav: () => void
  fecharNav: () => void
  alternarNav: () => void

  // Navegação de rota em andamento — alimenta a barra do <NavProgress>. Booleano puro:
  // `iniciar` = começou uma navegação, `finalizar` = a rota nova renderizou (ou o timeout
  // de segurança estourou). Sem contador — `iniciar`/`finalizar` não são 1:1 (o Next pode
  // chamar pushState+replaceState numa nav só, e navegação rápida pula rotas intermediárias).
  navegando: boolean
  iniciarNav: () => void
  finalizarNav: () => void

  // Selo "Inscrição feita!" mostrado logo após QUALQUER "Se inscrever" (grátis ou pago).
  // No evento pago dobra de "ponte" até a rota de checkout abrir (fora do app shell) —
  // `ponteSubtitulo` carrega a legenda "Abrindo o pagamento…" nesse caso, null no grátis.
  // Vive aqui, e não no botão, pra sobreviver ao drawer/modal que dispara a ação desmontar
  // no meio da transição.
  ponteCheckout: boolean
  ponteSubtitulo: string | null
  abrirPonteCheckout: (subtitulo?: string | null) => void
  fecharPonteCheckout: () => void

  // Animação de boas-vindas mostrada por cima do app shell logo que ele aparece.
  // `'completa'` = depois de um login de verdade (logo + saudação + cortina que sobe).
  // `'curta'` = ao voltar com sessão já ativa (fade de marca rápido, 1×/sessão do navegador).
  // `null` = nada rodando. `mostrarBoasVindas` não sobrescreve uma animação em andamento.
  boasVindas: 'completa' | 'curta' | null
  mostrarBoasVindas: (tipo: 'completa' | 'curta') => void
  encerrarBoasVindas: () => void
}

export const useUiStore = create<UiState>((set) => ({
  navAberta: false,
  abrirNav: () => set({ navAberta: true }),
  fecharNav: () => set({ navAberta: false }),
  alternarNav: () => set((s) => ({ navAberta: !s.navAberta })),

  navegando: false,
  iniciarNav: () => set((s) => (s.navegando ? s : { navegando: true })),
  finalizarNav: () => set((s) => (s.navegando ? { navegando: false } : s)),

  ponteCheckout: false,
  ponteSubtitulo: null,
  abrirPonteCheckout: (subtitulo) => set({ ponteCheckout: true, ponteSubtitulo: subtitulo ?? null }),
  fecharPonteCheckout: () => set((s) => (s.ponteCheckout ? { ponteCheckout: false } : s)),

  boasVindas: null,
  mostrarBoasVindas: (tipo) => set((s) => (s.boasVindas ? s : { boasVindas: tipo })),
  encerrarBoasVindas: () => set((s) => (s.boasVindas ? { boasVindas: null } : s)),
}))
