'use client';

import { useState } from 'react';

export default function CadastroWizardPage() {
    const [passo, setPasso] = useState(1);
    const [nomeIgreja, setNomeIgreja] = useState('');
    const [emailAdmin, setEmailAdmin] = useState('');
    const [nomeAdmin, setNomeAdmin] = useState('');
    const [senha, setSenha] = useState('');

    return (
        <div className="max-w-lg mx-auto py-12 px-4">
            <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Cadastro Domus</h1>
            <p className="text-sm text-gray-600 mb-8">
                Inicie 14 dias de teste gratuito sem cobrança imediata.
            </p>

            {passo === 1 ? (
                <form
                    onSubmit={(e) => {
                        e.preventDefault();
                        setPasso(2);
                    }}
                    className="space-y-4 bg-white p-6 rounded-xl border border-gray-200 shadow-sm"
                >
                    <h2 className="text-lg font-bold text-gray-800">Passo 1: Dados da Igreja & Administrador</h2>
                    <div>
                        <label htmlFor="nomeIgreja" className="block text-sm font-medium text-gray-700">
                            Nome da Igreja
                        </label>
                        <input
                            id="nomeIgreja"
                            value={nomeIgreja}
                            onChange={(e) => setNomeIgreja(e.target.value)}
                            required
                            placeholder="Ex: Igreja Evangélica da Graça"
                            className="w-full border rounded-lg p-2.5 mt-1 border-gray-300"
                        />
                    </div>
                    <div>
                        <label htmlFor="nomeAdmin" className="block text-sm font-medium text-gray-700">
                            Nome do Administrador
                        </label>
                        <input
                            id="nomeAdmin"
                            value={nomeAdmin}
                            onChange={(e) => setNomeAdmin(e.target.value)}
                            required
                            placeholder="Ex: Pastor João Silva"
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
                            placeholder="joao@igrejagraca.org"
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
                        className="w-full bg-blue-600 text-white font-bold py-3 rounded-lg hover:bg-blue-700 transition"
                    >
                        Avançar para Pagamento (14 dias grátis)
                    </button>
                </form>
            ) : (
                <div className="space-y-4 bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
                    <h2 className="text-lg font-bold text-gray-800">Passo 2: Cartão de Crédito para Validação</h2>
                    <p className="text-xs text-gray-500">
                        O cartão é validado agora para pré-autorizar a mensalidade. Nenhuma cobrança será feita nos primeiros 14 dias.
                    </p>
                    {/* Componente Mercado Pago Card Tokenizer */}
                    <div className="p-4 bg-gray-50 rounded border text-sm text-gray-600">
                        [ Form de Cartão Mercado Pago Subscriptions SDK ]
                    </div>
                    <button
                        type="button"
                        onClick={() => alert('Cadastro e trial ativados com sucesso!')}
                        className="w-full bg-green-600 text-white font-bold py-3 rounded-lg hover:bg-green-700 transition"
                    >
                        Concluir Cadastro & Iniciar 14 Dias Grátis
                    </button>
                </div>
            )}
        </div>
    );
}
