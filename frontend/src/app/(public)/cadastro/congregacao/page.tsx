'use client';

import { useState } from 'react';

export default function CadastroCongregacaoPage() {
    const [codigoConvite, setCodigoConvite] = useState('');
    const [nomeCongregacao, setNomeCongregacao] = useState('');
    const [emailAdmin, setEmailAdmin] = useState('');
    const [nomeAdmin, setNomeAdmin] = useState('');
    const [senha, setSenha] = useState('');
    const [sucesso, setSucesso] = useState(false);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setSucesso(true);
    };

    return (
        <div className="max-w-lg mx-auto py-12 px-4">
            <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Cadastro de Congregação</h1>
            <p className="text-sm text-gray-600 mb-8">
                Cadastre sua congregação sem custo utilizando o código de convite fornecido pela igreja matriz.
            </p>

            {sucesso ? (
                <div className="bg-green-50 border border-green-200 text-green-800 p-6 rounded-xl text-center">
                    <h2 className="text-xl font-bold mb-2">Congregação Cadastrada!</h2>
                    <p className="text-sm">Sua congregação foi vinculada à igreja matriz com sucesso.</p>
                </div>
            ) : (
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
                            className="w-full border rounded-lg p-2.5 mt-1 border-gray-300"
                        />
                    </div>
                    <button
                        type="submit"
                        className="w-full bg-green-600 text-white font-bold py-3 rounded-lg hover:bg-green-700 transition"
                    >
                        Concluir Cadastro Gratuitamente
                    </button>
                </form>
            )}
        </div>
    );
}
