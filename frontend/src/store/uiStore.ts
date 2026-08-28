import { create } from 'zustand'

// Estado de UI global e efêmero (não persiste).
interface UiState {
  navAberta: boolean
  abrirNav: () => void
  fecharNav: () => void
  alternarNav: () => void

  // Navegação em andamento — alimenta o <NavProgress>. Contador (não booleano) porque
  // dois cliques rápidos podem encavalar duas navegações; só zera quando todas terminam.
  navsPendentes: number
  navegando: boolean
  iniciarNav: () => void
  finalizarNav: () => void
  resetarNav: () => void
}

export const useUiStore = create<UiState>((set) => ({
  navAberta: false,
  abrirNav: () => set({ navAberta: true }),
  fecharNav: () => set({ navAberta: false }),
  alternarNav: () => set((s) => ({ navAberta: !s.navAberta })),

  navsPendentes: 0,
  navegando: false,
  iniciarNav: () =>
    set((s) => {
      const n = s.navsPendentes + 1
      return { navsPendentes: n, navegando: n > 0 }
    }),
  finalizarNav: () =>
    set((s) => {
      const n = Math.max(0, s.navsPendentes - 1)
      return { navsPendentes: n, navegando: n > 0 }
    }),
  resetarNav: () => set({ navsPendentes: 0, navegando: false }),
}))
