export interface PlanoServidor {
    id: string;
    nomeExibicao: string;
    limitePessoas: number;
    limiteCongregacoes: number;
    valorMensal: number;
    featuresHabilitadas: string[];
    descricoesFeaturesHabilitadas: string[];
}

export interface CadastroCongregacaoPayload {
    codigoConvite: string;
    nome: string;
    nomeAdmin: string;
    emailAdmin: string;
    senha: string;
}

export async function registrarCongregacaoFilha(payload: CadastroCongregacaoPayload): Promise<any> {
    const response = await fetch('/api/igrejas/registrar-congregacao', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
    });
    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || 'Falha ao cadastrar congregação');
    }
    return response.json();
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
