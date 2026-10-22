'use client';

import { useState, useEffect } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { motion, AnimatePresence } from 'motion/react';
import {
    Sparkles,
    Users,
    Calendar,
    DollarSign,
    Check,
    CheckCircle2,
    ChevronDown,
    ArrowRight,
    MessageSquare,
    Network,
    ShieldCheck
} from 'lucide-react';
import { buscarPlanosServidor, PlanoServidor } from '@/services/plano.service';
import styles from './LandingPage.module.css';

interface ModuloTour {
    id: string;
    nome: string;
    icone: typeof Users;
    tag: string;
    titulo: string;
    descricao: string;
    destaques: string[];
    cardTitulo: string;
    cardBadge: string;
    itensVisual: { rotulo: string; valor: string; status?: string }[];
}

const MODULOS_SISTEMA: ModuloTour[] = [
    {
        id: 'membros',
        nome: 'Membros & Pessoas',
        icone: Users,
        tag: 'Pastoreio & Cadastro',
        titulo: 'Ficha cadastral completa, cargos ministeriais e contatos',
        descricao: 'Cadastre e acompanhe membros e congregantes com foto de perfil, dados de contato, endereço para visitas pastorais e cargos ministeriais, sem planilhas soltas.',
        destaques: [
            'Distinção clara entre membros oficiais e congregantes da comunidade',
            'Histórico de cargos ministeriais e datas de aniversário para pastoreio',
            'Dados completos de endereço, telefone e e-mail em um só prontuário',
            'Controle estrito de acesso e proteção de dados em conformidade com a LGPD'
        ],
        cardTitulo: 'Ficha de Pessoa • Ativa',
        cardBadge: 'Cadastro Atualizado',
        itensVisual: [
            { rotulo: 'Pessoa', valor: 'Filipe Pereira', status: 'Membro Ativo' },
            { rotulo: 'Cargo / Função', valor: 'Diácono • Comunicação', status: 'Liderança' },
            { rotulo: 'Contato', valor: '(61) 99999-0000', status: 'WhatsApp' }
        ]
    },
    {
        id: 'celulas',
        nome: 'Células & Grupos',
        icone: Network,
        tag: 'Comunhão & Multiplicação',
        titulo: 'Gestão de pequenas comunidades, reuniões e anfitriões',
        descricao: 'Supervisione pequenos grupos e células com informações claras de líderes, anfitriões, dias de encontro, horários e membros vinculados a cada grupo.',
        destaques: [
            'Organização de células por líderes, co-líderes e anfitriões locais',
            'Cadastro de dias e horários de cada reunião semanal',
            'Vínculo direto de participantes cadastrados na igreja a cada célula',
            'Visão centralizada para coordenação pastoral de todas as células ativas'
        ],
        cardTitulo: 'Célula Boas Novas • Reunião Semanal',
        cardBadge: '14 Membros Vinculados',
        itensVisual: [
            { rotulo: 'Líder Responsável', valor: 'Matheus Costa', status: 'Ativo' },
            { rotulo: 'Dia & Horário', valor: 'Quarta-feira às 20h', status: 'Semanal' },
            { rotulo: 'Anfitrião', valor: 'Residência Família Costa', status: 'Confirmado' }
        ]
    },
    {
        id: 'eventos',
        nome: 'Eventos & Inscrições',
        icone: Calendar,
        tag: 'Inscrições & Cultos',
        titulo: 'Cultos, congressos e checkout integrado via Mercado Pago',
        descricao: 'Crie eventos gratuitos ou pagos em minutos com gestão de lotes, formulários personalizados de inscrição e baixa automática com pagamento PIX ou Cartão.',
        destaques: [
            'Integração direta com a conta Mercado Pago da igreja para PIX e Cartão',
            'Gestão de lotes com virada automática por data ou quantidade de vagas',
            'Acompanhamento e baixa automática de inscritos confirmados em tempo real',
            'Organização de cultos, cursos e conferências com controle de capacidade'
        ],
        cardTitulo: 'Conferência Anual Domus 2026',
        cardBadge: 'Lote 2 • 88% Ocupado',
        itensVisual: [
            { rotulo: 'Inscritos Confirmados', valor: '384 participantes', status: 'Confirmados' },
            { rotulo: 'Receita Arrecadada', valor: 'R$ 28.800,00', status: 'PIX e Cartão' },
            { rotulo: 'Controle de Vagas', valor: '88% do lote preenchido', status: 'Em andamento' }
        ]
    },
    {
        id: 'tesouraria',
        nome: 'Tesouraria & Balancete',
        icone: DollarSign,
        tag: 'Mordomia & Prestação',
        titulo: 'Entradas, dízimos, contas a pagar e balancete 12 meses',
        descricao: 'Uma tesouraria sem mistérios para pastores e diáconos. Lançamentos por categorias e contas, controle de despesas a pagar com alertas de vencimento e balancete anual automático.',
        destaques: [
            'Balancete anual contábil consolidado de 12 meses gerado em um clique',
            'Gestão de contas a pagar com alerta de vencimentos e controle de atrasos',
            'Registro sigiloso e categorizado de dízimos, ofertas e doações',
            'Relatórios claros prontos para apresentação e aprovação em assembleia'
        ],
        cardTitulo: 'Balancete Financeiro • Exercício 2026',
        cardBadge: 'Contas em Dia',
        itensVisual: [
            { rotulo: 'Dízimos & Ofertas', valor: 'R$ 48.650,00', status: 'Entradas' },
            { rotulo: 'Contas a Pagar', valor: 'R$ 31.200,00', status: '100% Quitadas' },
            { rotulo: 'Saldo Operacional', valor: 'R$ 17.450,00', status: 'Positivo' }
        ]
    },
    {
        id: 'filiais',
        nome: 'Congregações Multi-Sede',
        icone: Sparkles,
        tag: 'Expansão & Unidade',
        titulo: 'Vinculação matriz-filial ágil por código de convite',
        descricao: 'Gerencie igrejas filiais mantendo a autonomia pastoral de cada sede, com visibilidade consolidada de membros e balancetes no painel da igreja matriz.',
        destaques: [
            'Código de vínculo dinâmico para cadastrar e vincular filiais em 1 minuto',
            'Visão executiva agregada de membros ativos em todas as congregações',
            'Isolamento total de acessos operacionais entre congregações no banco de dados',
            'Balancete individual por filial ou relatório unificado da denominação'
        ],
        cardTitulo: 'Igreja Sede • 3 Filiais Vinculadas',
        cardBadge: 'Conectadas',
        itensVisual: [
            { rotulo: 'Congregação Asa Norte', valor: '180 membros', status: 'Vinculada' },
            { rotulo: 'Congregação Taguatinga', valor: '140 membros', status: 'Vinculada' },
            { rotulo: 'Congregação Águas Claras', valor: '95 membros', status: 'Vinculada' }
        ]
    },
    {
        id: 'comunidade',
        nome: 'Feed & Comunicação',
        icone: MessageSquare,
        tag: 'Comunhão Segura',
        titulo: 'Mural de avisos e devocionais em ambiente protegido',
        descricao: 'Substitua grupos desordenados de mensagens por um canal oficial onde a igreja recebe avisos urgentes, aniversariantes do mês e devocionais pastorais.',
        destaques: [
            'Feed social restrito exclusivamente aos membros cadastrados na igreja',
            'Fixação de avisos urgentes e comunicados pastorais no topo da página',
            'Lembretes automáticos de aniversariantes da semana para a liderança',
            'Ambiente limpo e espiritual, livre de propagandas ou distrações externas'
        ],
        cardTitulo: 'Mural Oficial da Comunidade',
        cardBadge: 'Aviso da Semana',
        itensVisual: [
            { rotulo: 'Devocional Pastoral', valor: 'Pr. Marcos: Firmeza na Fé', status: 'Publicado' },
            { rotulo: 'Vigília de Oração', valor: 'Sexta-feira às 22h no Templo', status: 'Aviso Fixado' },
            { rotulo: 'Aniversariantes', valor: '5 membros celebrando esta semana', status: 'Pastoreio' }
        ]
    }
];

const PLANOS_BASE = [
    {
        id: 'BASICO',
        nome: 'Básico',
        publico: 'Ideal para congregações locais em fase inicial',
        precoMensal: 49,
        pessoas: 'Até 50 pessoas ativas',
        cgs: '0 congregações filhas (Solo)',
        recursos: [
            'Até 50 pessoas ativas cadastradas',
            '0 congregações filhas (Igreja Solo)',
            'Ficha completa de membros e congregantes',
            'Células e grupos com dias e horários',
            'Eventos com perguntas personalizadas e Mercado Pago',
            'Contas a pagar com alertas de vencimento',
            'Tesouraria e balancete anual contábil',
            'Mural de avisos e feed da comunidade',
            'Suporte humanizado para sua liderança'
        ],
        popular: false
    },
    {
        id: 'PRO',
        nome: 'Pro',
        publico: 'A escolha mais completa para igrejas dinâmicas',
        precoMensal: 129,
        pessoas: 'Até 200 pessoas ativas',
        cgs: 'Até 2 congregações filhas inclusas',
        recursos: [
            'Todas as funcionalidades da plataforma Domus',
            'Até 200 pessoas ativas cadastradas',
            'Até 2 congregações filhas vinculadas',
            'Código de vínculo dinâmico para filiais',
            'Compartilhamento de eventos, avisos e feed com filiais',
            'Balancete e relatórios consolidados (sede + filiais)',
            'Suporte humanizado para sua liderança'
        ],
        popular: true
    },
    {
        id: 'PRO_PLUS',
        nome: 'Pro+',
        publico: 'Perfeito para igrejas consolidadas e múltiplas sedes',
        precoMensal: 249,
        pessoas: 'Até 600 pessoas ativas',
        cgs: 'Até 5 congregações filhas inclusas',
        recursos: [
            'Todas as funcionalidades da plataforma Domus',
            'Até 600 pessoas ativas cadastradas',
            'Até 5 congregações filhas vinculadas',
            'Código de vínculo dinâmico para filiais',
            'Compartilhamento de eventos, avisos e feed com filiais',
            'Balancete e relatórios consolidados (sede + filiais)',
            'Suporte humanizado para sua liderança'
        ],
        popular: false
    },
    {
        id: 'ENTERPRISE',
        nome: 'Enterprise',
        publico: 'Para grandes templos, ministérios e convenções',
        precoMensal: 449,
        pessoas: 'Pessoas ilimitadas',
        cgs: 'Congregações ilimitadas',
        recursos: [
            'Todas as funcionalidades da plataforma Domus',
            'Pessoas ativas cadastradas ilimitadas',
            'Congregações filhas vinculadas ilimitadas',
            'Código de vínculo dinâmico para filiais',
            'Compartilhamento de eventos, avisos e feed com toda a rede',
            'Balancete e relatórios consolidados de toda a denominação',
            'Suporte humanizado para sua liderança'
        ],
        popular: false
    }
];

const FAQS = [
    {
        pergunta: 'Preciso cadastrar cartão de crédito para iniciar o teste?',
        resposta: 'Sim. O cartão de crédito é cadastrado no início para ativar seus 14 dias de teste e garantir a segurança do ambiente, mas nenhuma cobrança é realizada no momento do cadastro. Você tem 14 dias completos para experimentar todas as funcionalidades sem pagar nada. Se optar por cancelar antes do término do período de teste, nenhuma mensalidade será cobrada.'
    },
    {
        pergunta: 'Como funciona a gestão de congregações filhas e multi-sede?',
        resposta: 'A igreja sede gera um código de vínculo único dentro do sistema. A liderança da congregação filha utiliza esse código no momento do cadastro e fica imediatamente vinculada à matriz, mantendo sua autonomia operacional local enquanto a matriz acompanha relatórios consolidados.'
    },
    {
        pergunta: 'Como funciona a cobrança de eventos e inscrições?',
        resposta: 'A sua igreja conecta a conta do Mercado Pago nas configurações do sistema. Ao criar um evento com inscrição paga (conferência, congresso, retiro), o sistema gera links seguros de inscrição. Os participantes pagam via PIX ou cartão de crédito e a baixa é automática: a lista de inscritos confirmados é atualizada em tempo real no painel administrativo, sem necessidade de conferência manual de comprovantes.'
    },
    {
        pergunta: 'Como funciona o cadastro e importação dos membros?',
        resposta: 'O cadastro é direto e intuitivo através do painel administrativo. Além disso, nossa equipe de suporte auxilia na organização inicial e orienta o processo de migração das informações da sua igreja.'
    },
    {
        pergunta: 'Os dados da igreja e os relatórios de dízimos estão seguros?',
        resposta: 'Absolutamente. Utilizamos criptografia de ponta a ponta, isolamento rigoroso por igreja em nível de banco de dados e servidores com backups redundantes automatizados, seguindo estritamente as diretrizes da LGPD.'
    }
];

export default function LandingPage() {
    const [activeTab, setActiveTab] = useState<string>('membros');
    const [openFaq, setOpenFaq] = useState<number | null>(null);
    const [planosServidor, setPlanosServidor] = useState<PlanoServidor[]>([]);

    useEffect(() => {
        buscarPlanosServidor()
            .then((data) => {
                if (Array.isArray(data)) {
                    setPlanosServidor(data);
                } else {
                    setPlanosServidor([]);
                }
            })
            .catch(() => setPlanosServidor([]));
    }, []);

    const moduloAtual = MODULOS_SISTEMA.find((m) => m.id === activeTab) || MODULOS_SISTEMA[0];

    const toggleFaq = (index: number) => {
        setOpenFaq(openFaq === index ? null : index);
    };

    return (
        <div className={styles.page}>
            {/* ─── Header & Nav ────────────────────────────────────── */}
            <header className={styles.header}>
                <nav className={styles.navContainer} aria-label="Navegação principal">
                    <Link href="/" className={styles.logo}>
                        <Image
                            src="/images/logo2.png"
                            alt="Domus"
                            width={22}
                            height={36}
                            priority
                            className={styles.logoImg}
                        />
                        <span className={styles.logoText}>Domus</span>
                    </Link>

                    <div className={styles.navLinks}>
                        <a href="#recursos" className={styles.navLink}>Recursos</a>
                        <a href="#modulos" className={styles.navLink}>Módulos</a>
                        <a href="#planos" className={styles.navLink}>Planos</a>
                        <a href="#faq" className={styles.navLink}>Dúvidas</a>
                    </div>

                    <div className={styles.navActions}>
                        <Link href="/login" className={styles.btnLogin}>
                            Entrar
                        </Link>
                        <Link href="/planos?origem=trial" className={styles.btnAcessar}>
                            <span>Começar grátis</span>
                            <ArrowRight size={16} aria-hidden="true" />
                        </Link>
                    </div>
                </nav>
            </header>

            <main>
                {/* ─── Hero Section ────────────────────────────────────── */}
                <section className={styles.heroSection}>
                    {/* Arcos Sacros Vetoriais Animados em SVG */}
                    <div className={styles.heroArches} aria-hidden="true">
                        <svg width="1200" height="800" viewBox="0 0 1200 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path className={styles.archPath} d="M100 800V400C100 123.858 323.858 -100 600 -100C876.142 -100 1100 123.858 1100 400V800" stroke="#004ac6" strokeWidth="1" />
                            <path className={styles.archPath} d="M250 800V400C250 206.7 406.7 50 600 50C793.3 50 950 206.7 950 400V800" stroke="#004ac6" strokeWidth="1" style={{ animationDelay: '0.4s' }} />
                            <path className={styles.archPath} d="M400 800V400C400 289.543 489.543 200 600 200C710.457 200 800 289.543 800 400V800" stroke="#004ac6" strokeWidth="1" style={{ animationDelay: '0.8s' }} />
                        </svg>
                    </div>

                    <div className={styles.heroContainer}>
                        {/* Coluna Esquerda: Mensagem Editorial */}
                        <motion.div
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.6, ease: 'easeOut' }}
                        >
                            <div className={styles.heroBadge}>
                                <span className={styles.pulseDot} aria-hidden="true" />
                                <span>SISTEMA DE GESTÃO PARA IGREJAS</span>
                            </div>

                            <h1 className={styles.heroTitle}>
                                Domus: <span className={styles.heroTitleItalic}>tecnologia</span> a serviço da fé.
                            </h1>

                            <p className={styles.heroSubtitle}>
                                Gerencie sua igreja com a serenidade de um sistema desenhado para servir. Membros, eventos e finanças em um único lugar, sem ruídos operacionais.
                            </p>

                            <div className={styles.heroCtas}>
                                <Link href="/planos?origem=trial" className={styles.btnPrimary}>
                                    <span>Começar teste grátis</span>
                                    <ArrowRight size={18} aria-hidden="true" />
                                </Link>
                                <a href="#planos" className={styles.btnSecondary}>
                                    Ver planos & recursos
                                </a>
                            </div>

                            <div className={styles.heroReassurance}>
                                <ShieldCheck size={16} aria-hidden="true" color="#004ac6" />
                                <span>14 dias de teste grátis • Sem cobrança imediata • Cancele quando quiser</span>
                            </div>
                        </motion.div>

                        {/* Coluna Direita: Tablet 3D Glassmorphism e Badges */}
                        <motion.div
                            className={styles.tabletWrapper}
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            transition={{ duration: 0.8, delay: 0.2, ease: 'easeOut' }}
                        >
                            <div className={styles.tabletGlow} aria-hidden="true" />

                            <div className={styles.tabletFrame}>
                                <div className={styles.tabletHeaderBar}>
                                    <div className={styles.tabletDots}>
                                        <span className={styles.tabletDot} style={{ background: '#ff5f56' }} />
                                        <span className={styles.tabletDot} style={{ background: '#ffbd2e' }} />
                                        <span className={styles.tabletDot} style={{ background: '#27c93f' }} />
                                    </div>
                                    <span className={styles.tabletUrl}>domus.app/painel/visao-geral</span>
                                    <span className={styles.tabletBadgeLive}>Tempo Real</span>
                                </div>

                                <div className={styles.tabletImageWrapper}>
                                    <Image
                                        src="/images/tablet-preview.png"
                                        alt="Interface moderna do painel administrativo Domus em tablet"
                                        width={680}
                                        height={450}
                                        priority
                                        className={styles.tabletImage}
                                    />
                                </div>
                            </div>

                            {/* Badge Flutuante 1: Dízimos Hoje */}
                            <div className={styles.floatingCardDizimo}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.6rem' }}>
                                    <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#dbe1ff', color: '#004ac6', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                        <DollarSign size={20} />
                                    </div>
                                    <div>
                                        <p style={{ fontSize: 10, fontWeight: 700, textTransform: 'uppercase', color: '#64748b', letterSpacing: '0.05em', margin: 0 }}>Dízimos & Ofertas Hoje</p>
                                        <p style={{ fontSize: 17, fontWeight: 800, color: '#131b2e', margin: 0 }}>R$ 12.450,00</p>
                                    </div>
                                </div>
                                <div style={{ width: '100%', height: 5, background: '#eaedff', borderRadius: 9999, overflow: 'hidden' }}>
                                    <div style={{ width: '78%', height: '100%', background: '#004ac6', borderRadius: 9999 }} />
                                </div>
                            </div>

                            {/* Badge Flutuante 2: Escala Confirmada */}
                            <div className={styles.floatingCardEscala}>
                                <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#e2fadb', color: '#16a34a', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                    <CheckCircle2 size={18} />
                                </div>
                                <div>
                                    <p style={{ fontSize: 10, fontWeight: 700, color: '#64748b', margin: 0, textTransform: 'uppercase' }}>Culto de Celebração</p>
                                    <p style={{ fontSize: 13, fontWeight: 700, color: '#131b2e', margin: 0 }}>Escala 100% Confirmada</p>
                                </div>
                            </div>
                        </motion.div>
                    </div>
                </section>

                {/* ─── Bento Grid Section ──────────────────────────────── */}
                <section id="recursos" className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <span className={styles.sectionTag}>ARQUITETURA INTENCIONAL</span>
                        <h2 className={styles.sectionTitle}>Funcionalidades desenhadas para servir</h2>
                        <p className={styles.sectionSubtitle}>
                            Acreditamos que a gestão não deve ser um fardo, mas uma ferramenta transparente para ampliar o alcance e a maturidade da sua igreja.
                        </p>
                    </div>

                    <div className={styles.bentoGrid}>
                        {/* Bento 1: Membros & Pessoas (Span 8) */}
                        <div className={`${styles.bentoCard} ${styles.bentoCol8}`}>
                            <div>
                                <div className={styles.cardIcon}>
                                    <Users size={24} aria-hidden="true" />
                                </div>
                                <h3 className={styles.bentoCardTitle}>Gestão de Membros & Pessoas</h3>
                                <p className={styles.bentoCardDesc}>
                                    Cadastre membros e congregantes com foto, dados de contato, endereço para visitas pastorais e cargos ministeriais.
                                </p>
                            </div>

                            <div className={styles.miniTable}>
                                <div className={styles.miniTableRow}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                                        <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#dbe1ff', color: '#004ac6', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 11 }}>FP</div>
                                        <div>
                                            <p style={{ margin: 0, fontWeight: 600, color: '#131b2e' }}>Filipe Oliveira</p>
                                            <p style={{ margin: 0, fontSize: 10, color: '#64748b' }}>Diácono • Membro Ativo</p>
                                        </div>
                                    </div>
                                    <span style={{ fontSize: 11, background: '#eaedff', color: '#004ac6', padding: '2px 8px', borderRadius: 9999, fontWeight: 600 }}>Membro</span>
                                </div>
                                <div className={styles.miniTableRow}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                                        <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#b5d0fd', color: '#003ea8', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 11 }}>MS</div>
                                        <div>
                                            <p style={{ margin: 0, fontWeight: 600, color: '#131b2e' }}>Mariana Silva</p>
                                            <p style={{ margin: 0, fontSize: 10, color: '#64748b' }}>Líder de Louvor</p>
                                        </div>
                                    </div>
                                    <span style={{ fontSize: 11, background: '#e2fadb', color: '#16a34a', padding: '2px 8px', borderRadius: 9999, fontWeight: 600 }}>Liderança</span>
                                </div>
                            </div>
                        </div>

                        {/* Bento 2: Eventos e Inscrições (Span 4) */}
                        <div className={`${styles.bentoCard} ${styles.bentoCol4}`}>
                            <div>
                                <div className={styles.cardIcon}>
                                    <Calendar size={24} aria-hidden="true" />
                                </div>
                                <h3 className={styles.bentoCardTitle}>Eventos & Inscrições</h3>
                                <p className={styles.bentoCardDesc}>
                                    Inscrições com pagamento automático via Mercado Pago e controle de participantes.
                                </p>
                            </div>

                            <div className={styles.progressContainer}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600 }}>
                                    <span style={{ color: '#131b2e' }}>Congresso Geral 2026</span>
                                    <span style={{ color: '#004ac6' }}>85% das vagas</span>
                                </div>
                                <div className={styles.progressBarTrack}>
                                    <div className={styles.progressBarFill} style={{ width: '85%' }} />
                                </div>
                                <p style={{ fontSize: 11, color: '#64748b', margin: 0 }}>340 de 400 inscrições confirmadas via PIX/Cartão</p>
                            </div>
                        </div>

                        {/* Bento 3: Finanças Transparentes (Span 5) */}
                        <div className={`${styles.bentoCard} ${styles.bentoCol5}`}>
                            <div>
                                <div className={styles.cardIcon}>
                                    <DollarSign size={24} aria-hidden="true" />
                                </div>
                                <h3 className={styles.bentoCardTitle}>Finanças Transparentes</h3>
                                <p className={styles.bentoCardDesc}>
                                    Dízimos, contas a pagar com lembretes e balancete anual em 12 meses.
                                </p>
                            </div>

                            <div>
                                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600, color: '#64748b' }}>
                                    <span>Distribuição do Orçamento</span>
                                    <span style={{ color: '#131b2e' }}>100% equilibrado</span>
                                </div>
                                <div className={styles.budgetSplitBar}>
                                    <div style={{ width: '40%', background: '#004ac6' }} title="Missões 40%" />
                                    <div style={{ width: '35%', background: '#2563eb' }} title="Manutenção 35%" />
                                    <div style={{ width: '25%', background: '#b5d0fd' }} title="Departamentos 25%" />
                                </div>
                                <div style={{ display: 'flex', gap: '0.75rem', fontSize: 10, color: '#434655' }}>
                                    <span>• Missões (40%)</span>
                                    <span>• Manutenção (35%)</span>
                                    <span>• Departamentos (25%)</span>
                                </div>
                            </div>
                        </div>

                        {/* Bento 4: Comunicação & Mural (Span 7) */}
                        <div className={`${styles.bentoCard} ${styles.bentoCol7}`}>
                            <div>
                                <div className={styles.cardIcon}>
                                    <MessageSquare size={24} aria-hidden="true" />
                                </div>
                                <h3 className={styles.bentoCardTitle}>Comunicação Unificada & Mural Oficial</h3>
                                <p className={styles.bentoCardDesc}>
                                    Canal seguro e exclusivo para os membros. Avisos urgentes, devocionais e aniversariantes sem ruídos de redes externas.
                                </p>
                            </div>

                            <div style={{ background: '#f2f3ff', borderRadius: 12, padding: '1rem', borderLeft: '4px solid #004ac6' }}>
                                <p style={{ fontSize: 10, fontWeight: 700, textTransform: 'uppercase', color: '#004ac6', margin: '0 0 4px 0' }}>COMUNICADO OFICIAL • FIXADO</p>
                                <p style={{ fontSize: 13, fontWeight: 600, color: '#131b2e', margin: '0 0 4px 0' }}>Assembleia Geral Ordinária de Membros</p>
                                <p style={{ fontSize: 12, color: '#434655', margin: 0 }}>Prestação de contas do 1º trimestre e votação do balancete anual no próximo domingo às 19h.</p>
                            </div>
                        </div>
                    </div>
                </section>

                {/* ─── Deep Modules Tabs Tour ──────────────────────────── */}
                <section id="modulos" className={styles.section} style={{ paddingTop: '2rem' }}>
                    <div className={styles.sectionHeader}>
                        <span className={styles.sectionTag}>EXPLORE EM DETALHES</span>
                        <h2 className={styles.sectionTitle}>Conheça os 6 pilares do Domus</h2>
                        <p className={styles.sectionSubtitle}>
                            Cada módulo foi desenhado pensando na rotina real dos pastores, líderes de células, tesoureiros e voluntários da igreja.
                        </p>
                    </div>

                    <div className={styles.tabsContainer}>
                        {/* Seletor de Abas com acessibilidade */}
                        <div className={styles.tabsNav} role="tablist" aria-label="Módulos da plataforma">
                            {MODULOS_SISTEMA.map((modulo) => {
                                const Icone = modulo.icone;
                                const isActive = activeTab === modulo.id;
                                return (
                                    <button
                                        key={modulo.id}
                                        role="tab"
                                        aria-selected={isActive}
                                        aria-controls={`panel-${modulo.id}`}
                                        id={`tab-${modulo.id}`}
                                        className={`${styles.tabBtn} ${isActive ? styles.tabBtnActive : ''}`}
                                        onClick={() => setActiveTab(modulo.id)}
                                    >
                                        <Icone size={16} aria-hidden="true" />
                                        <span>{modulo.nome}</span>
                                    </button>
                                );
                            })}
                        </div>

                        {/* Painel da Aba com Motion Animation */}
                        <AnimatePresence mode="wait">
                            <motion.div
                                key={moduloAtual.id}
                                id={`panel-${moduloAtual.id}`}
                                role="tabpanel"
                                aria-labelledby={`tab-${moduloAtual.id}`}
                                className={styles.tabContentPanel}
                                initial={{ opacity: 0, y: 15 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -15 }}
                                transition={{ duration: 0.35, ease: 'easeOut' }}
                            >
                                <div>
                                    <span style={{ fontSize: 11, fontWeight: 700, textTransform: 'uppercase', color: '#004ac6', letterSpacing: '0.08em' }}>
                                        {moduloAtual.tag}
                                    </span>
                                    <h3 style={{ fontSize: '1.85rem', fontWeight: 800, color: '#131b2e', margin: '0.5rem 0 1rem 0', lineHeight: 1.25 }}>
                                        {moduloAtual.titulo}
                                    </h3>
                                    <p style={{ fontSize: 15, color: '#434655', lineHeight: 1.65, margin: 0 }}>
                                        {moduloAtual.descricao}
                                    </p>

                                    <ul className={styles.tabFeatureList}>
                                        {moduloAtual.destaques.map((item, idx) => (
                                            <li key={idx} className={styles.tabFeatureItem}>
                                                <span className={styles.checkBullet}>
                                                    <Check size={13} aria-hidden="true" />
                                                </span>
                                                <span>{item}</span>
                                            </li>
                                        ))}
                                    </ul>

                                    <Link href="/planos?origem=trial" className={styles.btnPrimary} style={{ width: 'fit-content' }}>
                                        <span>Experimentar este módulo</span>
                                        <ArrowRight size={16} aria-hidden="true" />
                                    </Link>
                                </div>

                                {/* Cartão Visual Ilustrativo do Módulo */}
                                <div className={styles.tabVisualCard}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <h4 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: '#131b2e' }}>
                                            {moduloAtual.cardTitulo}
                                        </h4>
                                        <span style={{ fontSize: 11, fontWeight: 700, color: '#004ac6', background: '#dbe1ff', padding: '3px 8px', borderRadius: 9999 }}>
                                            {moduloAtual.cardBadge}
                                        </span>
                                    </div>

                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                                        {moduloAtual.itensVisual.map((item, idx) => (
                                            <div key={idx} style={{ background: '#ffffff', borderRadius: 10, padding: '0.85rem 1.1rem', border: '1px solid rgba(195,198,215,0.3)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                <div>
                                                    <p style={{ margin: 0, fontSize: 11, color: '#64748b' }}>{item.rotulo}</p>
                                                    <p style={{ margin: 0, fontSize: 13, fontWeight: 600, color: '#131b2e' }}>{item.valor}</p>
                                                </div>
                                                {item.status && (
                                                    <span style={{ fontSize: 11, color: '#004ac6', fontWeight: 600, background: '#eaedff', padding: '2px 8px', borderRadius: 6 }}>
                                                        {item.status}
                                                    </span>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </motion.div>
                        </AnimatePresence>
                    </div>
                </section>

                {/* ─── Planos & Precificação ───────────────────────────── */}
                <section id="planos" className={styles.section}>
                    <div className={styles.sectionHeader}>
                        <span className={styles.sectionTag}>INVESTIMENTO TRANSPARENTE</span>
                        <h2 className={styles.sectionTitle}>Escolha o plano ideal para a sua comunidade</h2>
                        <p className={styles.sectionSubtitle}>
                            Todas as modalidades contam com 14 dias de teste gratuito sem cobrança imediata. Altere ou cancele a qualquer momento com um clique.
                        </p>
                    </div>

                    {/* Grid de 4 Cards de Planos */}
                    <div className={styles.plansGrid}>
                        {PLANOS_BASE.map((plano) => {
                            const doServidor = Array.isArray(planosServidor) ? planosServidor.find((s) => s.id === plano.id) : undefined;
                            const valorExibido = doServidor ? Math.round(doServidor.valorMensal) : plano.precoMensal;

                            const limitePessoasTexto = doServidor
                                ? (doServidor.limitePessoas === 99999 ? 'Pessoas ilimitadas' : `Até ${doServidor.limitePessoas} pessoas ativas`)
                                : plano.pessoas;

                            const limiteCgsTexto = doServidor
                                ? (doServidor.limiteCongregacoes === 0 ? '0 congregações (Solo)' : doServidor.limiteCongregacoes === 9999 ? 'Congregações ilimitadas' : `Até ${doServidor.limiteCongregacoes} congregações filhas`)
                                : plano.cgs;

                            return (
                                <div
                                    key={plano.id}
                                    className={`${styles.planCard} ${plano.popular ? styles.planCardFeatured : ''}`}
                                >
                                    {plano.popular && (
                                        <span className={styles.planFeaturedTag}>MAIS ESCOLHIDO</span>
                                    )}

                                    <div className={styles.planHeader}>
                                        <h3 className={styles.planTitle}>{plano.nome}</h3>
                                        <p className={styles.planAudience}>{plano.publico}</p>
                                    </div>

                                    <div className={styles.planPriceArea}>
                                        <span className={styles.currency}>R$</span>
                                        <span className={styles.priceNumber}>{valorExibido}</span>
                                        <span className={styles.period}>/mês</span>
                                    </div>

                                    <ul className={styles.planFeatureList}>
                                        <li className={styles.planFeatureItem}>
                                            <Check size={16} className={styles.planFeatureCheck} aria-hidden="true" />
                                            <span><strong>{limitePessoasTexto}</strong></span>
                                        </li>
                                        <li className={styles.planFeatureItem}>
                                            <Check size={16} className={styles.planFeatureCheck} aria-hidden="true" />
                                            <span><strong>{limiteCgsTexto}</strong></span>
                                        </li>
                                        {plano.recursos.map((rec, i) => (
                                            <li key={i} className={styles.planFeatureItem}>
                                                <Check size={16} className={styles.planFeatureCheck} aria-hidden="true" />
                                                <span>{rec}</span>
                                            </li>
                                        ))}
                                    </ul>

                                    <Link
                                        href={`/cadastro?plano=${plano.id}`}
                                        className={`${styles.btnPlanAction} ${plano.popular ? styles.btnPlanPrimary : styles.btnPlanOutline}`}
                                    >
                                        Começar teste de 14 dias
                                    </Link>
                                </div>
                            );
                        })}
                    </div>

                    {/* Matriz Comparativa Detalhada */}
                    <div className={styles.tableWrapper}>
                        <div className={styles.tableCardHeader}>
                            <span className={styles.tableTag}>TABELA COMPARATIVA</span>
                            <h3 className={styles.tableCardTitle}>
                                Comparativo de Recursos por Modalidade
                            </h3>
                            <p className={styles.tableCardSubtitle}>
                                Compare os limites e recursos de cada plano lado a lado
                            </p>
                        </div>

                        <div className={styles.mobileScrollHint} aria-hidden="true">
                            <ArrowRight size={14} />
                            <span>Deslize para o lado para ver todos os planos</span>
                        </div>

                        <table className={styles.compareTable}>
                            <thead>
                                <tr>
                                    <th className={styles.thFeature}>Capacidades & Módulos</th>
                                    <th className={styles.thPlan}>Básico</th>
                                    <th className={`${styles.thPlan} ${styles.thPlanFeatured}`}>
                                        <span className={styles.tagProTable}>Mais Escolhido</span>
                                        Pro
                                    </th>
                                    <th className={styles.thPlan}>Pro+</th>
                                    <th className={styles.thPlan}>Enterprise</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Pessoas Ativas Cadastradas</td>
                                    <td className={styles.tdValue}>Até 50 pessoas</td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>Até 200 pessoas</td>
                                    <td className={styles.tdValue}>Até 600 pessoas</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.badgeUnlimited}>Ilimitado</span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Congregações Filhas Inclusas</td>
                                    <td className={styles.tdValue}>0 (Solo)</td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>Até 2 filiais</td>
                                    <td className={styles.tdValue}>Até 5 filiais</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.badgeUnlimited}>Ilimitadas</span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Ficha Cadastral de Membros & Congregantes</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Células & Grupos (Dias e Horários)</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Contas a Pagar & Alerta de Vencimentos</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Eventos & Inscrições (com Perguntas Personalizadas e Mercado Pago)</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Código de Vínculo Rápido para Filiais</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableDash}>—</span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Compartilhamento com Filiais (Eventos, Avisos e Feed)</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableDash}>—</span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Balancete Anual e Relatórios (Próprio e Consolidado)</td>
                                    <td className={styles.tdValue}>Próprio (1 sede)</td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured}`}>Consolidado (3 sedes)</td>
                                    <td className={styles.tdValue}>Consolidado (6 sedes)</td>
                                    <td className={styles.tdValue}>Consolidado Geral</td>
                                </tr>
                                <tr className={styles.compareRow}>
                                    <td className={styles.tdFeature}>Suporte Humanizado à Liderança</td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={`${styles.tdValue} ${styles.tdFeatured} ${styles.tdFeaturedLast}`}>
                                        <span className={`${styles.tableCheckIcon} ${styles.tableCheckIconFeatured}`}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                    <td className={styles.tdValue}>
                                        <span className={styles.tableCheckIcon}><Check size={14} strokeWidth={2.5} /></span>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </section>

                {/* ─── FAQ Sanfona Section ─────────────────────────────── */}
                <section id="faq" className={styles.section} style={{ paddingTop: '2rem' }}>
                    <div className={styles.sectionHeader}>
                        <span className={styles.sectionTag}>DÚVIDAS FREQUENTES</span>
                        <h2 className={styles.sectionTitle}>Perguntas e Respostas</h2>
                        <p className={styles.sectionSubtitle}>
                            Tudo o que você precisa saber sobre a segurança, faturamento e implantação do Domus na sua comunidade.
                        </p>
                    </div>

                    <div className={styles.faqContainer}>
                        {FAQS.map((faq, idx) => {
                            const isOpen = openFaq === idx;
                            return (
                                <div
                                    key={idx}
                                    className={`${styles.faqItem} ${isOpen ? styles.faqItemOpen : ''}`}
                                >
                                    <button
                                        type="button"
                                        className={styles.faqQuestionBtn}
                                        onClick={() => toggleFaq(idx)}
                                        aria-expanded={isOpen}
                                    >
                                        <span>{faq.pergunta}</span>
                                        <ChevronDown
                                            size={18}
                                            aria-hidden="true"
                                            style={{
                                                transform: isOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                                                transition: 'transform 0.25s ease'
                                            }}
                                        />
                                    </button>

                                    {isOpen && (
                                        <div className={styles.faqAnswer}>
                                            {faq.resposta}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </section>

                {/* ─── Final CTA Dark Navy ─────────────────────────────── */}
                <section className={styles.finalCtaSection}>
                    <div className={styles.finalCtaSvg} aria-hidden="true">
                        <svg width="1000" height="600" viewBox="0 0 1000 600" fill="none">
                            <path d="M0 600 C 300 100, 700 100, 1000 600" stroke="#ffffff" strokeWidth="1" />
                            <path d="M100 600 C 350 200, 650 200, 900 600" stroke="#ffffff" strokeWidth="1" />
                        </svg>
                    </div>

                    <div className={styles.finalCtaContainer}>
                        <div className={styles.finalCtaBadge}>
                            <Sparkles size={14} aria-hidden="true" />
                            <span>TRANSFORME A GESTÃO DA SUA IGREJA</span>
                        </div>

                        <h2 className={styles.finalCtaTitle}>
                            Pronto para viver a serenidade de um sistema feito com propósito?
                        </h2>

                        <p className={styles.finalCtaSubtitle}>
                            Experimente o Domus gratuitamente por 14 dias sem cobrança imediata. Altere ou cancele a qualquer momento.
                        </p>

                        <div className={styles.finalCtaActions}>
                            <Link href="/planos?origem=trial" className={styles.btnWhiteCta}>
                                <span>Começar teste grátis</span>
                                <ArrowRight size={18} aria-hidden="true" />
                            </Link>
                            <a
                                href="https://wa.me/5583994023173?text=Ol%C3%A1!+Gostaria+de+conhecer+uma+demonstra%C3%A7%C3%A3o+do+Domus+para+minha+igreja."
                                target="_blank"
                                rel="noopener noreferrer"
                                className={styles.btnGhostWhiteCta}
                            >
                                <span>Falar com consultor</span>
                            </a>
                        </div>

                        <div className={styles.finalCtaReassurance}>
                            <ShieldCheck size={15} aria-hidden="true" />
                            <span>No ar desde 2026 • Tecnologia eclesial segura e moderna</span>
                        </div>
                    </div>
                </section>
            </main>

            {/* ─── Institutional Footer ────────────────────────────── */}
            <footer className={styles.footer}>
                <div className={styles.footerContainer}>
                    <div>
                        <div className={styles.logo}>
                            <Image
                                src="/images/logo2.png"
                                alt="Domus"
                                width={20}
                                height={34}
                                className={styles.logoImg}
                            />
                            <span className={styles.logoText}>Domus</span>
                        </div>
                        <p className={styles.footerBrandDesc}>
                            Plataforma de gestão integrada desenhada com sobriedade arquitetônica para servir pastores, lideranças e congregações.
                        </p>
                    </div>

                    <div>
                        <h4 className={styles.footerColTitle}>Plataforma</h4>
                        <ul className={styles.footerList}>
                            <li><a href="#recursos" className={styles.footerLink}>Recursos</a></li>
                            <li><a href="#modulos" className={styles.footerLink}>Módulos do Sistema</a></li>
                            <li><a href="#planos" className={styles.footerLink}>Planos e Preços</a></li>
                            <li><a href="#faq" className={styles.footerLink}>Dúvidas Frequentes</a></li>
                        </ul>
                    </div>

                    <div>
                        <h4 className={styles.footerColTitle}>Módulos</h4>
                        <ul className={styles.footerList}>
                            <li><a href="#modulos" className={styles.footerLink}>Membros & Pessoas</a></li>
                            <li><a href="#modulos" className={styles.footerLink}>Células & Grupos</a></li>
                            <li><a href="#modulos" className={styles.footerLink}>Eventos & Inscrições</a></li>
                            <li><a href="#modulos" className={styles.footerLink}>Tesouraria & Balancete</a></li>
                        </ul>
                    </div>

                    <div>
                        <h4 className={styles.footerColTitle}>Jurídico & Suporte</h4>
                        <ul className={styles.footerList}>
                            <li><Link href="/termos" className={styles.footerLink}>Termos de Uso</Link></li>
                            <li><Link href="/privacidade" className={styles.footerLink}>Privacidade</Link></li>
                            <li><Link href="/seguranca" className={styles.footerLink}>Segurança & LGPD</Link></li>
                            <li><Link href="/login" className={styles.footerLink}>Acessar Minha Conta</Link></li>
                        </ul>
                    </div>
                </div>

                <div className={styles.footerBottom}>
                    <p style={{ margin: 0 }}>© 2026 Domus Tecnologia Eclesial. Todos os direitos reservados.</p>
                    <p style={{ margin: 0 }}>Tecnologia a serviço da fé.</p>
                </div>
            </footer>
        </div>
    );
}
