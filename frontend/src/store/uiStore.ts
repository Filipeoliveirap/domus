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
}

export const useUiStore = create<UiState>((set) => ({
  navAberta: false,
  abrirNav: () => set({ navAberta: true }),
  fecharNav: () => set({ navAberta: false }),
  alternarNav: () => set((s) => ({ navAberta: !s.navAberta })),

  navegando: false,
  iniciarNav: () => set((s) => (s.navegando ? s : { navegando: true })),
  finalizarNav: () => set((s) => (s.navegando ? { navegando: false } : s)),
}))
