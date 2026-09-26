'use client';

import { Suspense, useEffect, useState } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { registrarCongregacaoFilha } from '@/services/plano.service';
import { useAuthStore } from '@/store/authStore';
import styles from './cadastroCongregacao.module.css';

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

        if (!codigoConvite || !nomeCongregacao || !emailAdmin || !nomeAdmin || !senha) {
            setErroGeral('Preencha todos os campos obrigatórios.');
            return;
        }

        setIsLoading(true);

        try {
            const res = await registrarCongregacaoFilha({
                codigoConvite,
                nome: nomeCongregacao,
                nomeAdmin,
                emailAdmin,
                senha,
            });

            login({
                id: res.usuarioId,
                pessoaId: null,
                nome: res.nomeAdmin,
                role: { id: 'r-admin', nome: res.role },
                igrejaId: res.igrejaId,
                igrejaNome: res.nomeIgreja,
                fotoId: null,
                cargo: 'Administrador',
                igrejaSigla: null,
                igrejaLogoId: null,
                capacidadesExtras: [],
                precisaAceitarTermos: false,
                termosAceitosEm: new Date().toISOString(),
                rotulos: null,
                igrejaMaeId: null,
                statusAssinatura: 'ATIVA',
            });

            router.push('/inicio');
        } catch (err: any) {
            setErroGeral(err.message || 'Erro ao cadastrar congregação. Verifique o código de convite.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className={styles.container}>
            <div className={styles.card}>
                <h1 className={styles.titulo}>Cadastrar Congregação</h1>
                <p className={styles.subtitulo}>
                    Sua congregação fará parte da rede da igreja matriz usando o código de convite fornecido.
                </p>

                <form onSubmit={handleSubmit} className={styles.form}>
                    <div className={styles.campoGroup}>
                        <label className={styles.label} htmlFor="codigoConvite">Código de Vínculo da Matriz</label>
                        <input
                            id="codigoConvite"
                            type="text"
                            value={codigoConvite}
                            onChange={(e) => setCodigoConvite(e.target.value.toUpperCase())}
                            placeholder="Ex: DOMUS-XXXX"
                            className={styles.input}
                            required
                        />
                    </div>

                    <div className={styles.campoGroup}>
                        <label className={styles.label}>Nome da Congregação</label>
                        <input
                            type="text"
                            value={nomeCongregacao}
                            onChange={(e) => setNomeCongregacao(e.target.value)}
                            placeholder="Ex: Domus Congregação Bairro Alto"
                            className={styles.input}
                            required
                        />
                    </div>

                    <div className={styles.campoGroup}>
                        <label className={styles.label}>Nome do Responsável / Admin</label>
                        <input
                            type="text"
                            value={nomeAdmin}
                            onChange={(e) => setNomeAdmin(e.target.value)}
                            placeholder="Ex: Pr. Marcos Oliveira"
                            className={styles.input}
                            required
                        />
                    </div>

                    <div className={styles.campoGroup}>
                        <label className={styles.label}>E-mail de Login do Admin</label>
                        <input
                            type="email"
                            value={emailAdmin}
                            onChange={(e) => setEmailAdmin(e.target.value)}
                            placeholder="admin@congregacao.com"
                            className={styles.input}
                            required
                        />
                    </div>

                    <div className={styles.campoGroup}>
                        <label className={styles.label}>Senha de Acesso</label>
                        <input
                            type="password"
                            value={senha}
                            onChange={(e) => setSenha(e.target.value)}
                            placeholder="••••••••"
                            className={styles.input}
                            required
                        />
                    </div>

                    {erroGeral && (
                        <div className={styles.caixaErro}>
                            {erroGeral}
                        </div>
                    )}

                    <button
                        type="submit"
                        disabled={isLoading}
                        className={styles.btnSubmit}
                    >
                        {isLoading ? 'Cadastrando...' : 'Concluir Cadastro Gratuitamente'}
                    </button>

                    <div className={styles.footerLink}>
                        <Link href="/login" className={styles.link}>
                            Já tem conta? Voltar ao login
                        </Link>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default function CadastroCongregacaoPage() {
    return (
        <Suspense fallback={<div style={{ textAlign: 'center', padding: '48px', color: 'var(--color-text-secondary)' }}>Carregando...</div>}>
            <CadastroCongregacaoConteudo />
        </Suspense>
    );
}
