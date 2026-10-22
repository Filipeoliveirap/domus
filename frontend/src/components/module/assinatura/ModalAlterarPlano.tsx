'use client'

import React, { useState } from 'react'
import { X, Check, AlertCircle } from 'lucide-react'
import { PlanoServidor } from '@/services/plano.service'
import { alterarPlanoAssinatura, contratarPlanoAssinatura, ResumoAssinatura } from '@/services/assinatura.service'
import styles from './ModalAlterarPlano.module.css'

interface ModalAlterarPlanoProps {
    resumo: ResumoAssinatura;
    planos: PlanoServidor[];
    aoFechar: () => void;
    aoSucesso: () => void;
}

export function ModalAlterarPlano({ resumo, planos, aoFechar, aoSucesso }: ModalAlterarPlanoProps) {
    const [selecionado, setSelecionado] = useState<string>(resumo.plano)
    const [salvando, setSalvando] = useState(false)
    const [erro, setErro] = useState<string | null>(null)

    const handleConfirmar = async () => {
        if (resumo.status !== 'PAUSADA' && selecionado === resumo.plano) {
            aoFechar()
            return
        }

        setSalvando(true)
        setErro(null)
        try {
            if (resumo.status === 'PAUSADA') {
                await contratarPlanoAssinatura(selecionado)
            } else {
                await alterarPlanoAssinatura(selecionado)
            }
            aoSucesso()
        } catch (err: any) {
            setErro(err.response?.data?.message || err.message || 'Erro ao processar plano.')
        } finally {
            setSalvando(false)
        }
    }

    return (
        <div className={styles.overlay} onClick={aoFechar}>
            <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
                <div className={styles.cabecalho}>
                    <h2 className={styles.titulo}>
                        {resumo.status === 'PAUSADA' ? 'Contratar Plano Domus' : 'Alterar Plano Domus'}
                    </h2>
                    <button type="button" onClick={aoFechar} className={styles.btnFechar} aria-label="Fechar">
                        <X size={20} />
                    </button>
                </div>

                <div className={styles.gridPlanos}>
                    {planos.filter((p) => p.id !== 'PREMIUM').map((p) => {
                        const ehAtual = p.id === resumo.plano
                        const invalidoPorPessoas = resumo.totalPessoas > p.limitePessoas
                        const invalidoPorCongregacoes = resumo.totalCongregacoes > p.limiteCongregacoes
                        const desabilitado = invalidoPorPessoas || invalidoPorCongregacoes

                        return (
                            <div
                                key={p.id}
                                className={`
                                    ${styles.cardOpcao}
                                    ${selecionado === p.id ? styles.cardSelecionado : ''}
                                    ${desabilitado ? styles.cardInvalido : ''}
                                `}
                                onClick={() => !desabilitado && setSelecionado(p.id)}
                            >
                                <div>
                                    <div className={styles.nomePlano}>
                                        {p.nomeExibicao} {ehAtual && '(Plano Atual)'}
                                    </div>
                                    <div className={styles.detalhesPlano}>
                                        Até {p.limitePessoas === 99999 ? 'Ilimitadas' : p.limitePessoas} pessoas •{' '}
                                        {p.limiteCongregacoes === 0 ? 'Sem filiais' : `${p.limiteCongregacoes} congregações`}
                                    </div>
                                    {desabilitado && (
                                        <div className={styles.erro}>
                                            <AlertCircle size={14} aria-hidden="true" /> Limite atual excede o plano
                                        </div>
                                    )}
                                </div>
                                <div className={styles.precoPlano}>
                                    R$ {p.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}/mês
                                </div>
                            </div>
                        )
                    })}
                </div>

                {erro && <div className={styles.erro}>{erro}</div>}

                <div className={styles.acoes}>
                    <button type="button" onClick={aoFechar} className={styles.btnCancelar} disabled={salvando}>
                        Cancelar
                    </button>
                    <button
                        type="button"
                        onClick={handleConfirmar}
                        className={styles.btnConfirmar}
                        disabled={salvando || (resumo.status !== 'PAUSADA' && selecionado === resumo.plano)}
                    >
                        {salvando ? 'Processando...' : resumo.status === 'PAUSADA' ? 'Contratar Plano' : 'Confirmar Alteração'}
                    </button>
                </div>
            </div>
        </div>
    )
}
