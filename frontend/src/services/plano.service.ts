export interface PlanoServidor {
    id: string;
    nomeExibicao: string;
    limitePessoas: number;
    limiteCongregacoes: number;
    valorMensal: number;
    featuresHabilitadas: string[];
    descricoesFeaturesHabilitadas: string[];
}

export async function buscarPlanosServidor(): Promise<PlanoServidor[]> {
    try {
        const response = await fetch('/api/planos');
        if (!response.ok) {
            throw new Error('Falha ao carregar planos');
        }
        return await response.json();
    } catch {
        // Fallback defensivo em caso de offline/erro de rede
        return [
            {
                id: 'BASICO',
                nomeExibicao: 'Básico',
                limitePessoas: 60,
                limiteCongregacoes: 0,
                valorMensal: 79,
                featuresHabilitadas: [],
                descricoesFeaturesHabilitadas: ['Pessoas & Células', 'Financeiro Básico']
            },
            {
                id: 'PRO',
                nomeExibicao: 'Pro',
                limitePessoas: 300,
                limiteCongregacoes: 3,
                valorMensal: 179,
                featuresHabilitadas: ['FEED_SOCIAL', 'CONTAS_A_PAGAR', 'CHECKOUT_EVENTO'],
                descricoesFeaturesHabilitadas: ['Mural & Feed Social', 'Contas a Pagar', 'Cobrança de Eventos']
            }
        ];
    }
}
