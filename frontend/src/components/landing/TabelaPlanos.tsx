'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { buscarPlanosServidor, PlanoServidor } from '@/services/plano.service';

export default function TabelaPlanos() {
    const [planos, setPlanos] = useState<PlanoServidor[]>([]);
    const [carregando, setCarregando] = useState(true);

    useEffect(() => {
        buscarPlanosServidor().then((res) => {
            setPlanos(res);
            setCarregando(false);
        });
    }, []);

    if (carregando) {
        return <div className="p-8 text-center text-gray-500">Carregando catálogo de planos do servidor...</div>;
    }

    return (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 p-6">
            {planos.map((p) => {
                const popular = p.id === 'PRO';
                return (
                    <div
                        key={p.id}
                        className={`border rounded-xl p-6 shadow-sm flex flex-col justify-between ${
                            popular ? 'border-blue-600 ring-2 ring-blue-600' : 'border-gray-200'
                        }`}
                    >
                        <div>
                            {popular && (
                                <span className="bg-blue-600 text-white text-xs font-bold px-3 py-1 rounded-full uppercase">
                                    Mais Popular
                                </span>
                            )}
                            <h3 className="text-2xl font-bold mt-2">{p.nomeExibicao}</h3>
                            <p className="text-sm font-semibold text-gray-700 mt-2">
                                Até {p.limitePessoas} pessoas ativas
                            </p>
                            <p className="text-xs text-gray-500 mt-1">
                                {p.limiteCongregacoes === 0
                                    ? '0 congregações (Solo)'
                                    : `Até ${p.limiteCongregacoes} congregações filhas`}
                            </p>

                            <p className="text-4xl font-extrabold mt-6 text-gray-900">
                                R$ {p.valorMensal}<span className="text-sm font-normal text-gray-500">/mês</span>
                            </p>

                            <div className="mt-6 space-y-2 border-t pt-4 text-sm">
                                <p className="font-semibold text-gray-700">Recursos Incluídos:</p>
                                {p.descricoesFeaturesHabilitadas.map((desc, i) => (
                                    <p key={i} className="text-gray-600 flex items-center gap-2">
                                        <span className="text-green-600">✓</span> {desc}
                                    </p>
                                ))}
                            </div>
                        </div>

                        <Link
                            href={`/cadastro?plano=${p.id}`}
                            className={`mt-8 block text-center font-bold py-3 px-4 rounded-lg transition ${
                                popular
                                    ? 'bg-blue-600 text-white hover:bg-blue-700'
                                    : 'bg-gray-900 text-white hover:bg-gray-800'
                            }`}
                        >
                            Testar 14 dias grátis
                        </Link>
                    </div>
                );
            })}
        </div>
    );
}
