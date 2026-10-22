import { api } from '@/lib/api'

export interface ResumoAssinatura {
    plano: 'BASICO' | 'PRO' | 'PRO_PLUS' | 'ENTERPRISE';
    nomePlano: string;
    valorMensal: number;
    status: 'TRIAL' | 'ATIVA' | 'PAUSADA' | 'CANCELADA';
    diasRestantesTrial: number;
    totalPessoas: number;
    limitePessoas: number;
    percentualPessoas: number;
    totalCongregacoes: number;
    limiteCongregacoes: number;
    percentualCongregacoes: number;
    ehMatriz: boolean;
    nomeMatriz?: string | null;
    cicloExpiraEm?: string | null;
    diasRestantesCiclo?: number | null;
}

export async function obterResumoAssinatura(): Promise<ResumoAssinatura> {
    const response = await api.get<ResumoAssinatura>('/igrejas/minha/assinatura');
    return response.data;
}

export async function alterarPlanoAssinatura(novoPlano: string): Promise<void> {
    await api.post('/igrejas/minha/assinatura/alterar-plano', { novoPlano });
}

export async function atualizarCartaoAssinatura(cardTokenId: string): Promise<void> {
    await api.put('/igrejas/minha/assinatura/cartao', { cardTokenId });
}

export async function cancelarAssinatura(): Promise<void> {
    await api.post('/igrejas/minha/assinatura/cancelar');
}

export async function reativarAssinatura(): Promise<void> {
    await api.post('/igrejas/minha/assinatura/reativar');
}

export async function contratarPlanoAssinatura(plano: string, cardTokenId?: string): Promise<void> {
    await api.post('/igrejas/minha/assinatura/contratar', { plano, cardTokenId });
}
