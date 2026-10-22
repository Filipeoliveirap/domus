'use client'

import React, { useState } from 'react'
import Link from 'next/link'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronRight, Sparkles, CheckCircle2, CreditCard, ArrowUpRight, AlertOctagon, AlertTriangle, Lock, Church, Info } from 'lucide-react'
import { obterResumoAssinatura, reativarAssinatura } from '@/services/assinatura.service'
import { buscarPlanosServidor } from '@/services/plano.service'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarAssinatura } from '@/lib/permissoes'
import { ModalAtualizarCartao } from '@/components/module/assinatura/ModalAtualizarCartao'
import { ModalCancelarAssinatura } from '@/components/module/assinatura/ModalCancelarAssinatura'
import { SkeletonAssinatura } from './SkeletonAssinatura'
import { Transicao } from '@/components/common/Transicao/Transicao'
import styles from './assinatura.module.css'

export default function ConfigAssinaturaPage() {
    const queryClient = useQueryClient()
    const role = useAuthStore((s) => s.role)
    const ehAdmin = podeGerenciarAssinatura(role)
    const [modalCartaoAberto, setModalCartaoAberto] = useState(false)
    const [modalCancelarAberto, setModalCancelarAberto] = useState(false)

    const { data: resumo, isLoading: carregandoResumo } = useQuery({
        queryKey: ['assinatura-resumo'],
        queryFn: obterResumoAssinatura,
        staleTime: 0,
        refetchOnMount: 'always',
        refetchOnWindowFocus: true,
    })

    const { data: planos = [] } = useQuery({
        queryKey: ['planos-servidor'],
        queryFn: buscarPlanosServidor,
        staleTime: 1000 * 60 * 30,
    })

    const handleAtualizacaoSucesso = () => {
        queryClient.invalidateQueries({ queryKey: ['assinatura-resumo'] })
        setModalCartaoAberto(false)
        setModalCancelarAberto(false)
    }

    const handleReativar = async () => {
        try {
            await reativarAssinatura()
            queryClient.invalidateQueries({ queryKey: ['assinatura-resumo'] })
        } catch (err: any) {
            alert(err.message || 'Erro ao reativar assinatura')
        }
    }

    if (carregandoResumo) {
        return <SkeletonAssinatura />
    }

    if (!resumo) {
        return (
            <div className={styles.container}>
                <p style={{ padding: '32px', textAlign: 'center', color: 'var(--color-danger)' }}>
                    Não foi possível carregar as informações de assinatura.
                </p>
            </div>
        )
    }

    const ehTrial = resumo.status === 'TRIAL'
    const ehCancelada = resumo.status === 'CANCELADA'

    return (
        <Transicao modo="fade">
            <div className={styles.container}>
            <div className={styles.card}>
                <div className={styles.cabecalhoPlano}>
                    <div className={styles.planoInfo}>
                        <span className={styles.badgePlano}>
                            {resumo.ehMatriz ? (
                                <>
                                    <Sparkles size={13} aria-hidden="true" />
                                    Plano Atual
                                </>
                            ) : (
                                <>
                                    <Church size={13} aria-hidden="true" />
                                    Congregação Vinculada
                                </>
                            )}
                        </span>
                        <h2 className={styles.nomePlano}>
                            {resumo.ehMatriz
                                ? (resumo.plano
                                    ? `${resumo.nomePlano} (R$ ${resumo.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })} / mês)`
                                    : 'Nenhum plano contratado')
                                : `Plano ${resumo.nomePlano} (Coberto pela ${resumo.nomeMatriz || 'Igreja Sede'})`
                            }
                        </h2>
                        {!resumo.ehMatriz ? (
                            <p className={styles.statusTrial} style={{ color: '#16a34a' }}>
                                Status: Cobertura Ativa • Mensalidade isenta
                            </p>
                        ) : (
                            <>
                                {resumo.status === 'PAUSADA' && (
                                    <p className={styles.statusTrial} style={{ color: '#d97706' }}>
                                        Status: Assinatura Pausada • Modo Somente Leitura
                                    </p>
                                )}
                                {ehTrial && (
                                    <p className={styles.statusTrial}>
                                        Status: Período de Testes ({resumo.diasRestantesTrial} {resumo.diasRestantesTrial === 1 ? 'dia restante' : 'dias restantes'})
                                    </p>
                                )}
                                {ehCancelada && (
                                    <p className={styles.statusTrial} style={{ color: '#dc2626' }}>
                                        Status: Assinatura Cancelada • Acesso garantido até{' '}
                                        {resumo.cicloExpiraEm
                                            ? new Date(resumo.cicloExpiraEm).toLocaleDateString('pt-BR')
                                            : 'o término do ciclo'}{' '}
                                        ({resumo.diasRestantesCiclo ?? 0} {resumo.diasRestantesCiclo === 1 ? 'dia restante' : 'dias restantes'})
                                    </p>
                                )}
                            </>
                        )}
                    </div>
                    <span className={ehCancelada || resumo.status === 'PAUSADA' ? styles.badgeCancelada : styles.badgeEmDia}>
                        {ehCancelada || resumo.status === 'PAUSADA' ? <AlertTriangle size={14} /> : <CheckCircle2 size={14} aria-hidden="true" />}
                        {ehCancelada ? 'Cancelada' : resumo.status === 'PAUSADA' ? 'Pausada' : 'Em dia'}
                    </span>
                </div>

                <div className={styles.gridConsumo}>
                    <div className={styles.boxConsumo}>
                        <div className={styles.consumoTopo}>
                            <p className={styles.labelConsumo}>Consumo de Pessoas Ativas</p>
                        </div>
                        <p className={styles.valorConsumo}>
                            {resumo.totalPessoas} de {resumo.limitePessoas === 99999 ? 'Ilimitadas' : resumo.limitePessoas} pessoas
                        </p>
                        <div className={styles.barraProgressoBg}>
                            <div
                                className={styles.barraProgressoFill}
                                style={{
                                    width: `${resumo.percentualPessoas}%`,
                                    background: resumo.percentualPessoas > 80 ? '#f59e0b' : 'var(--color-primary)'
                                }}
                            ></div>
                        </div>
                    </div>

                    {!resumo.ehMatriz ? (
                        <div className={styles.boxConsumo}>
                            <div className={styles.consumoTopo}>
                                <p className={styles.labelConsumo}>Rede de Congregações</p>
                            </div>
                            <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', margin: '0 0 6px 0', lineHeight: 1.4 }}>
                                Sua igreja faz parte da rede da <strong>{resumo.nomeMatriz || 'Igreja Sede'}</strong>.
                            </p>
                            <p className={styles.valorConsumo}>
                                {resumo.totalCongregacoes} de {resumo.limiteCongregacoes} congregações na rede
                            </p>
                            <div className={styles.barraProgressoBg}>
                                <div
                                    className={styles.barraProgressoFill}
                                    style={{
                                        width: `${resumo.percentualCongregacoes}%`,
                                        background: 'var(--color-primary)'
                                    }}
                                ></div>
                            </div>
                        </div>
                    ) : resumo.limiteCongregacoes > 0 ? (
                        <div className={styles.boxConsumo}>
                            <div className={styles.consumoTopo}>
                                <p className={styles.labelConsumo}>Congregações Vinculadas</p>
                            </div>
                            <p className={styles.valorConsumo}>
                                {resumo.totalCongregacoes} de {resumo.limiteCongregacoes} congregações
                            </p>
                            <div className={styles.barraProgressoBg}>
                                <div
                                    className={styles.barraProgressoFill}
                                    style={{
                                        width: `${resumo.percentualCongregacoes}%`,
                                        background: resumo.percentualCongregacoes > 80 ? '#f59e0b' : 'var(--color-primary)'
                                    }}
                                ></div>
                            </div>
                        </div>
                    ) : (
                        <div className={styles.boxBloqueado}>
                            <div className={styles.bloqueadoHeader}>
                                <Lock size={14} />
                                <span>Congregações Filhas</span>
                            </div>
                            <p className={styles.bloqueadoTexto}>
                                Exclusivo a partir do Plano Pro (até 2 congregações filhas inclusas).
                            </p>
                            {ehAdmin && (
                                <Link
                                    href="/planos"
                                    className={styles.btnUpgradeInline}
                                >
                                    <span>Fazer upgrade para liberar filiais</span>
                                    <ArrowUpRight size={14} />
                                </Link>
                            )}
                        </div>
                    )}
                </div>

                {!resumo.ehMatriz ? (
                    <div style={{
                        marginTop: '20px',
                        padding: '16px 20px',
                        background: 'var(--color-bg-page, #f8fafc)',
                        border: '1px solid var(--color-border, #e2e8f0)',
                        borderRadius: 'var(--radius-md, 8px)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '16px',
                        flexWrap: 'wrap'
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <Info size={18} color="var(--color-primary, #2563eb)" style={{ flexShrink: 0 }} />
                            <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary, #475569)', lineHeight: 1.4 }}>
                                A gestão de plano, cobrança e faturamento é centralizada na igreja matriz <strong>{resumo.nomeMatriz || 'Sede'}</strong>.
                            </p>
                        </div>
                        <Link href="/configuracoes/igrejas-vinculadas" className={styles.btnSecondary} style={{ textDecoration: 'none' }}>
                            <Church size={15} />
                            <span>Ver Igrejas Vinculadas</span>
                        </Link>
                    </div>
                ) : !ehAdmin ? (
                    <div style={{
                        marginTop: '20px',
                        padding: '14px 18px',
                        background: 'var(--color-bg-page, #f8fafc)',
                        border: '1px solid var(--color-border, #e2e8f0)',
                        borderRadius: 'var(--radius-md, 8px)',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '10px'
                    }}>
                        <Lock size={16} color="var(--color-text-muted, #64748b)" style={{ flexShrink: 0 }} />
                        <p style={{ margin: 0, fontSize: '13px', color: 'var(--color-text-secondary, #475569)', lineHeight: 1.4 }}>
                            A gestão de plano, cobrança e cartões da igreja é restrita a administradores.
                        </p>
                    </div>
                ) : (
                    <div className={styles.botoesAcao}>
                        <Link
                            href="/planos"
                            className={styles.btnPrimary}
                            style={{ textDecoration: 'none' }}
                        >
                            <span>{resumo.status === 'PAUSADA' ? 'Contratar Plano' : 'Alterar Plano'}</span>
                            <ArrowUpRight size={16} />
                        </Link>
                        {resumo.status !== 'PAUSADA' && (
                            <button
                                type="button"
                                onClick={() => setModalCartaoAberto(true)}
                                className={styles.btnSecondary}
                            >
                                <CreditCard size={16} />
                                <span>Atualizar Cartão de Crédito</span>
                            </button>
                        )}
                        {ehCancelada ? (
                            <button
                                type="button"
                                onClick={handleReativar}
                                className={styles.btnReativar}
                            >
                                <Sparkles size={16} />
                                <span>Reativar Assinatura</span>
                            </button>
                        ) : resumo.status !== 'PAUSADA' ? (
                            <button
                                type="button"
                                onClick={() => setModalCancelarAberto(true)}
                                className={styles.btnDanger}
                            >
                                <AlertOctagon size={16} />
                                <span>Cancelar Assinatura</span>
                            </button>
                        ) : null}
                    </div>
                )}
            </div>

            {modalCartaoAberto && (
                <ModalAtualizarCartao
                    aoFechar={() => setModalCartaoAberto(false)}
                    aoSucesso={handleAtualizacaoSucesso}
                />
            )}

            {modalCancelarAberto && (
                <ModalCancelarAssinatura
                    aoFechar={() => setModalCancelarAberto(false)}
                    aoSucesso={handleAtualizacaoSucesso}
                />
            )}
            </div>
        </Transicao>
    )
}
