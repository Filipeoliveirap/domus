import TabelaPlanos from '@/components/landing/TabelaPlanos';

export default function PlanosPage() {
    return (
        <main className="max-w-7xl mx-auto py-12 px-4">
            <h1 className="text-4xl font-extrabold text-center text-gray-900 mb-4">
                Escolha o plano ideal para sua igreja
            </h1>
            <p className="text-center text-gray-600 mb-10 max-w-2xl mx-auto">
                Cadastre sua igreja e test por 14 dias sem cobrança imediata. Altere ou cancele a qualquer momento.
            </p>
            <TabelaPlanos />
        </main>
    );
}
