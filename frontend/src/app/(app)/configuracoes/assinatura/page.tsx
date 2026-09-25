'use client';

export default function ConfigAssinaturaPage() {
    return (
        <div className="max-w-4xl mx-auto py-8 px-4 space-y-6">
            <h1 className="text-3xl font-extrabold text-gray-900">Minha Assinatura Domus</h1>

            <div className="bg-white border border-gray-200 rounded-xl p-6 shadow-sm space-y-6">
                <div className="flex justify-between items-start border-b pb-4">
                    <div>
                        <span className="bg-blue-100 text-blue-800 text-xs font-bold px-2.5 py-0.5 rounded uppercase">
                            Plano Atual
                        </span>
                        <h2 className="text-2xl font-bold mt-1 text-gray-900">Pro (R$ 179,00 / mês)</h2>
                        <p className="text-sm text-gray-500 mt-1">Status: TRIAL (11 dias restantes)</p>
                    </div>
                    <span className="bg-green-100 text-green-800 text-xs font-semibold px-3 py-1 rounded-full">
                        Em dia
                    </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="p-4 bg-gray-50 rounded-lg border border-gray-100">
                        <p className="text-xs text-gray-500 uppercase font-semibold">Consumo de Pessoas Ativas</p>
                        <p className="text-xl font-extrabold text-gray-800 mt-1">142 de 300 pessoas</p>
                        <div className="w-full bg-gray-200 h-2 rounded-full mt-2">
                            <div className="bg-blue-600 h-2 rounded-full w-1/2"></div>
                        </div>
                    </div>

                    <div className="p-4 bg-gray-50 rounded-lg border border-gray-100">
                        <p className="text-xs text-gray-500 uppercase font-semibold">Congregações Vinculadas</p>
                        <p className="text-xl font-extrabold text-gray-800 mt-1">1 de 3 congregações</p>
                        <div className="w-full bg-gray-200 h-2 rounded-full mt-2">
                            <div className="bg-green-600 h-2 rounded-full w-1/3"></div>
                        </div>
                    </div>
                </div>

                <div className="pt-4 flex flex-wrap gap-4 border-t">
                    <button className="bg-blue-600 text-white font-bold px-5 py-2.5 rounded-lg hover:bg-blue-700 transition text-sm">
                        Alterar Plano
                    </button>
                    <button className="border border-gray-300 text-gray-700 font-semibold px-5 py-2.5 rounded-lg hover:bg-gray-50 transition text-sm">
                        Atualizar Cartão de Crédito
                    </button>
                    <button className="border border-red-600 text-red-600 font-semibold px-5 py-2.5 rounded-lg hover:bg-red-50 transition text-sm">
                        Cancelar Assinatura
                    </button>
                </div>
            </div>
        </div>
    );
}
