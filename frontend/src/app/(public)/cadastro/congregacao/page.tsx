'use client';

import { Suspense, useEffect, useState } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { registrarCongregacaoFilha } from '@/services/plano.service';
import { useAuthStore } from '@/store/authStore';

function CadastroCongregacaoConteudo() {
    const searchParams = useSearchParams();
    const router = useRouter();
    const login = useAuthStore((state) => state.login);

    const [codigoConvite, setCodigoConvite] = useState('');
    const [nomeCongregacao, setNomeCongregacao] = useState('');
    const [emailAdmin, setEmailAdmin] = useState('');
    const [nomeAdmin, setNomeAdmin] = useState('');
    const [senha, setSenha] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [erroGeral, setErroGeral] = useState<string | null>(null);

    useEffect(() => {
        const codigoUrl = searchParams.get('codigo');
        if (codigoUrl) {
            setCodigoConvite(codigoUrl.toUpperCase());
        }
    }, [searchParams]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErroGeral(null);
        setIsLoading(true);

        try {
            const sessao = await registrarCongregacaoFilha({
                codigoConvite: codigoConvite.trim(),
                nome: nomeCongregacao.trim(),
                nomeAdmin: nomeAdmin.trim(),
                emailAdmin: emailAdmin.trim(),
                senha,
            });

            login(sessao);
            router.push('/inicio');
        } catch (err: any) {
            setErroGeral(err.message || 'Ocorreu um erro ao registrar a congregação.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="max-w-lg mx-auto py-12 px-4">
            <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Cadastro de Congregação</h1>
            <p className="text-sm text-gray-600 mb-8">
                Cadastre sua congregação sem custo utilizando o código de convite fornecido pela igreja matriz.
            </p>

            <form onSubmit={handleSubmit} className="space-y-4 bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
                <div>
                    <label htmlFor="codigoConvite" className="block text-sm font-medium text-gray-700">
                        Código de Convite da Matriz
                    </label>
                    <input
                        id="codigoConvite"
                        value={codigoConvite}
                        onChange={(e) => setCodigoConvite(e.target.value.toUpperCase())}
                        required
                        placeholder="DOMUS-X7Y9Z0"
                        className="w-full border rounded-lg p-2.5 mt-1 border-gray-300 font-mono text-center uppercase"
                    />
                </div>

                <div>
                    <label htmlFor="nomeCongregacao" className="block text-sm font-medium text-gray-700">
                        Nome da Congregação
                    </label>
                    <input
                        id="nomeCongregacao"
                        value={nomeCongregacao}
                        onChange={(e) => setNomeCongregacao(e.target.value)}
                        required
                        placeholder="Ex: Igreja Graça - Bairro Centenário"
                        className="w-full border rounded-lg p-2.5 mt-1 border-gray-300"
                    />
                </div>

                <div>
                    <label htmlFor="nomeAdmin" className="block text-sm font-medium text-gray-700">
                        Nome do Líder / Administrador
                    </label>
                    <input
                        id="nomeAdmin"
                        value={nomeAdmin}
                        onChange={(e) => setNomeAdmin(e.target.value)}
                        required
                        placeholder="Ex: Presbítero Marcos"
                        className="w-full border rounded-lg p-2.5 mt-1 border-gray-300"
                    />
                </div>

                <div>
                    <label htmlFor="emailAdmin" className="block text-sm font-medium text-gray-700">
                        E-mail do Administrador
                    </label>
                    <input
                        id="emailAdmin"
                        type="email"
                        value={emailAdmin}
                        onChange={(e) => setEmailAdmin(e.target.value)}
                        required
                        placeholder="marcos@igrejagraca.org"
                        className="w-full border rounded-lg p-2.5 mt-1 border-gray-300"
                    />
                </div>

                <div>
                    <label htmlFor="senha" className="block text-sm font-medium text-gray-700">
                        Senha
                    </label>
                    <input
                        id="senha"
                        type="password"
                        value={senha}
                        onChange={(e) => setSenha(e.target.value)}
                        required
                        minLength={6}
                        className="w-full border rounded-lg p-2.5 mt-1 border-gray-300"
                    />
                </div>

                {erroGeral && (
                    <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded-lg">
                        {erroGeral}
                    </div>
                )}

                <button
                    type="submit"
                    disabled={isLoading}
                    className="w-full bg-green-600 text-white font-bold py-3 rounded-lg hover:bg-green-700 transition disabled:opacity-50"
                >
                    {isLoading ? 'Cadastrando...' : 'Concluir Cadastro Gratuitamente'}
                </button>

                <div className="text-center pt-2">
                    <Link href="/login" className="text-xs text-gray-500 hover:underline">
                        Já tem conta? Voltar ao login
                    </Link>
                </div>
            </form>
        </div>
    );
}

export default function CadastroCongregacaoPage() {
    return (
        <Suspense fallback={<div className="p-12 text-center text-gray-500">Carregando...</div>}>
            <CadastroCongregacaoConteudo />
        </Suspense>
    );
}
