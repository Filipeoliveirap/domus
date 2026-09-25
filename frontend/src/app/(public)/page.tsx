'use client';

import Link from 'next/link';
import styles from './LandingPage.module.css';

const PLANOS = [
    {
        id: 'BASICO',
        nome: 'Básico',
        preco: '79',
        pessoas: 'Até 60 pessoas ativas',
        cgs: '0 congregações (Solo)',
        recursos: ['Pessoas & Células', 'Financeiro (Entradas/Saídas)', 'Eventos Simples'],
    },
    {
        id: 'PRO',
        nome: 'Pro',
        preco: '179',
        pessoas: 'Até 300 pessoas ativas',
        cgs: 'Até 3 congregações filhas',
        recursos: ['Mural & Feed Social', 'Contas a Pagar', 'Cobrança de Eventos (PIX/Cartão)', 'Relatórios Avançados'],
        popular: true,
    },
    {
        id: 'PRO_PLUS',
        nome: 'Pro+',
        preco: '299',
        pessoas: 'Até 800 pessoas ativas',
        cgs: 'Até 5 congregações filhas',
        recursos: ['Todas as funcionalidades', 'Código de convite para 5 filhas', 'Avisos por e-mail'],
    },
    {
        id: 'ENTERPRISE',
        nome: 'Enterprise',
        preco: '499',
        pessoas: 'Pessoas ilimitadas',
        cgs: 'Congregações ilimitadas',
        recursos: ['Todas as funcionalidades', 'Congregações ilimitadas', 'Gerente dedicado & SLA'],
    },
];

export default function LandingPage() {
    return (
        <div className={styles.page}>
            {/* Header */}
            <header className={styles.header}>
                <nav className={styles.navContainer}>
                    <div className={styles.logo}>DOMUS</div>
                    <div className={styles.navLinks}>
                        <a href="#recursos" className={styles.navLink}>Funcionalidades</a>
                        <a href="#planos" className={styles.navLink}>Preços</a>
                        <Link href="/login" className={styles.btnAcessar}>
                            Acessar minha conta
                        </Link>
                    </div>
                </nav>
            </header>

            <main>
                {/* Hero */}
                <section className={styles.heroSection}>
                    <div className={styles.heroContainer}>
                        <div>
                            <div className={styles.heroBadge}>
                                ✨ Gestão Eclesial Moderna
                            </div>
                            <h1 className={styles.heroTitle}>
                                Domus: <span className={styles.heroTitleItalic}>tecnologia</span> a serviço da fé.
                            </h1>
                            <p className={styles.heroSubtitle}>
                                Gerencie sua igreja com a serenidade de um sistema desenhado para servir. Membros, eventos e finanças em um único lugar.
                            </p>
                            <div className={styles.heroCtas}>
                                <Link href="/cadastro?plano=PRO" className={styles.btnPrimary}>
                                    Cadastrar minha igreja
                                </Link>
                                <a href="#planos" className={styles.btnSecondary}>
                                    Ver planos
                                </a>
                            </div>
                        </div>

                        <div>
                            <div className={styles.bentoCard}>
                                <h3>Resumo da Igreja</h3>
                                <p style={{ fontSize: '2rem', fontWeight: 'bold', color: 'var(--color-primary)' }}>
                                    142 Membros
                                </p>
                                <p style={{ color: 'var(--color-success)', fontWeight: 'bold' }}>
                                    ✓ Financeiro em Dia
                                </p>
                            </div>
                        </div>
                    </div>
                </section>

                {/* Features Grid */}
                <section id="recursos" className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <h2 className={styles.sectionTitle}>Funcionalidades desenhadas para servir</h2>
                        <p className={styles.sectionSubtitle}>
                            Acreditamos que a gestão não deve ser um fardo, mas uma ferramenta para ampliar o alcance da sua missão.
                        </p>
                    </div>

                    <div className={styles.bentoGrid}>
                        <div className={styles.bentoLarge}>
                            <div className={styles.cardIcon}>👥</div>
                            <h3>Gestão de Membros & Células</h3>
                            <p className={styles.sectionSubtitle}>
                                Acompanhe a jornada de cada pessoa na sua comunidade com ficha cadastral, histórico de batismo e organização de células.
                            </p>
                        </div>
                        <div className={styles.bentoCard}>
                            <div className={styles.cardIcon}>📅</div>
                            <h3>Eventos & Escalas</h3>
                            <p className={styles.sectionSubtitle}>
                                Organize cultos, conferências e inscrições de forma simples e intuitiva.
                            </p>
                        </div>
                        <div className={styles.bentoCard}>
                            <div className={styles.cardIcon}>💰</div>
                            <h3>Finanças Transparentes</h3>
                            <p className={styles.sectionSubtitle}>
                                Controle entradas, saídas e relatórios para tesouraria com segurança.
                            </p>
                        </div>
                    </div>
                </section>

                {/* Planos Section */}
                <section id="planos" className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <h2 className={styles.sectionTitle}>Escolha o plano ideal para a sua comunidade</h2>
                        <p className={styles.sectionSubtitle}>
                            Todas as modalidades contam com 14 dias de teste gratuito sem cobrança imediata.
                        </p>
                    </div>

                    <div className={styles.plansGrid}>
                        {PLANOS.map((p) => (
                            <div
                                key={p.id}
                                className={`${styles.planCard} ${p.popular ? styles.planCardPopular : ''}`}
                            >
                                {p.popular && <span className={styles.badgePopular}>Mais Escolhido</span>}
                                <div>
                                    <h3 className={styles.planName}>{p.nome}</h3>
                                    <p className={styles.planDesc}>{p.pessoas} • {p.cgs}</p>
                                    <div className={styles.planPrice}>
                                        R$ {p.preco}<span className={styles.planPeriod}>/mês</span>
                                    </div>
                                    <ul className={styles.planList}>
                                        {p.recursos.map((rec, i) => (
                                            <li key={i} className={styles.planItem}>
                                                <span className={styles.checkIcon}>✓</span> {rec}
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                                <Link
                                    href={`/cadastro?plano=${p.id}`}
                                    className={p.popular ? styles.btnPrimary : styles.btnSecondary}
                                >
                                    Testar 14 dias grátis
                                </Link>
                            </div>
                        ))}
                    </div>

                    {/* Matriz Comparativa */}
                    <div className={styles.tableWrapper}>
                        <h3 className={styles.sectionTitle} style={{ fontSize: '1.5rem', marginBottom: '1.5rem' }}>
                            Matriz Refinada de Recursos
                        </h3>
                        <table className={styles.table}>
                            <thead>
                                <tr>
                                    <th>Recurso / Capacidade</th>
                                    <th>Básico</th>
                                    <th>Pro</th>
                                    <th>Pro+</th>
                                    <th>Enterprise</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Pessoas Ativas</td>
                                    <td>Até 60</td>
                                    <td>Até 300</td>
                                    <td>Até 800</td>
                                    <td>Ilimitado</td>
                                </tr>
                                <tr>
                                    <td>Congregações Filhas</td>
                                    <td>0 (Solo)</td>
                                    <td>Até 3</td>
                                    <td>Até 5</td>
                                    <td>Ilimitado</td>
                                </tr>
                                <tr>
                                    <td>Mural & Feed Social</td>
                                    <td>—</td>
                                    <td>✓</td>
                                    <td>✓</td>
                                    <td>✓</td>
                                </tr>
                                <tr>
                                    <td>Contas a Pagar</td>
                                    <td>—</td>
                                    <td>✓</td>
                                    <td>✓</td>
                                    <td>✓</td>
                                </tr>
                                <tr>
                                    <td>Checkout PIX/Cartão em Eventos</td>
                                    <td>—</td>
                                    <td>✓</td>
                                    <td>✓</td>
                                    <td>✓</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </section>
            </main>

            {/* Footer */}
            <footer className={styles.footer}>
                <div className={styles.footerContainer}>
                    <div>
                        <div className={styles.logo}>DOMUS</div>
                        <p className={styles.sectionSubtitle} style={{ marginTop: '0.5rem' }}>
                            Tecnologia a serviço da fé.
                        </p>
                    </div>
                </div>
                <div className={styles.footerCopy}>
                    © 2026 Domus Tecnologia Eclesial. Todos os direitos reservados.
                </div>
            </footer>
        </div>
    );
}
