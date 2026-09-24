import Link from 'next/link';

const PLANOS = [
    {
        id: 'BASICO',
        nome: 'Básico',
        preco: '79,00',
        pessoas: 'Até 60 pessoas ativas',
        cgs: '0 congregações (Apenas solo)',
        recursos: ['Pessoas & Células', 'Financeiro (Entradas/Saídas)', 'Eventos Simples'],
        bloqueados: ['Mural & Feed Social', 'Contas a Pagar', 'Cobrança de Eventos (PIX/Cartão)']
    },
    {
        id: 'PRO',
        nome: 'Pro',
        preco: '179,00',
        pessoas: 'Até 300 pessoas ativas',
        cgs: 'Até 3 congregações filhas',
        recursos: ['Mural & Feed Social', 'Contas a Pagar', 'Cobrança de Eventos (PIX/Cartão)', 'QR Check-in & Relatórios'],
        destaque: true
    },
    {
        id: 'PRO_PLUS',
        nome: 'Pro+',
        preco: '299,00',
        pessoas: 'Até 800 pessoas ativas',
        cgs: 'Até 5 congregações filhas',
        recursos: ['Todas as funcionalidades liberadas', 'Código de convite para 5 filhas']
    },
    {
        id: 'ENTERPRISE',
        nome: 'Enterprise',
        preco: '499,00',
        pessoas: 'Pessoas ilimitadas',
        cgs: 'Congregações ilimitadas',
        recursos: ['Todas as funcionalidades liberadas', 'Congregações ilimitadas', 'Suporte prioritário']
    }
];

export default function TabelaPlanos() {
    return (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 p-6">
            {PLANOS.map((p) => (
                <div
                    key={p.id}
                    className={`border rounded-xl p-6 shadow-sm flex flex-col justify-between ${
                        p.destaque ? 'border-blue-600 ring-2 ring-blue-600' : 'border-gray-200'
                    }`}
                >
                    <div>
                        {p.destaque && (
                            <span className="bg-blue-600 text-white text-xs font-bold px-3 py-1 rounded-full uppercase">
                                Mais Popular
                            </span>
                        )}
                        <h3 className="text-2xl font-bold mt-2">{p.nome}</h3>
                        <p className="text-sm font-semibold text-gray-700 mt-2">{p.pessoas}</p>
                        <p className="text-xs text-gray-500 mt-1">{p.cgs}</p>

                        <p className="text-4xl font-extrabold mt-6 text-gray-900">
                            R$ {p.preco}<span className="text-sm font-normal text-gray-500">/mês</span>
                        </p>

                        <div className="mt-6 space-y-2 border-t pt-4 text-sm">
                            <p className="font-semibold text-gray-700">Inclui:</p>
                            {p.recursos.map((rec, i) => (
                                <p key={i} className="text-gray-600 flex items-center gap-2">
                                    <span className="text-green-600">✓</span> {rec}
                                </p>
                            ))}
                        </div>
                    </div>

                    <Link
                        href={`/cadastro?plano=${p.id}`}
                        className={`mt-8 block text-center font-bold py-3 px-4 rounded-lg transition ${
                            p.destaque
                                ? 'bg-blue-600 text-white hover:bg-blue-700'
                                : 'bg-gray-900 text-white hover:bg-gray-800'
                        }`}
                    >
                        Testar 14 dias grátis
                    </Link>
                </div>
            ))}
        </div>
    );
}
