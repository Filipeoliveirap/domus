import { create } from 'zustand'

export type MotivoModoLeitura = 'DESVINCULADA' | 'CARTAO_PENDENTE'

interface ModoLeituraState {
  aberto: boolean
  motivo: MotivoModoLeitura
  mensagem: string | null
  abrir: (motivo: MotivoModoLeitura, mensagem?: string | null) => void
  fechar: () => void
}

export const useModoLeituraStore = create<ModoLeituraState>((set) => ({
  aberto: false,
  motivo: 'DESVINCULADA',
  mensagem: null,
  abrir: (motivo, mensagem = null) => set({ aberto: true, motivo, mensagem }),
  fechar: () => set({ aberto: false, mensagem: null }),
}))
