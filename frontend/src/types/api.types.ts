import type { Impedimento } from '@/types/inscricao.type'

export interface ApiError {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  campos?: Record<string, string>;
  /** Presente só no 422 de NAO_ELEGIVEL (`ErrorResponse.ofElegibilidade`). */
  impedimentos?: Impedimento[];
}