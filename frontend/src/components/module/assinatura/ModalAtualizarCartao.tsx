'use client'

import React, { useState } from 'react'
import { X, CreditCard, Lock, ShieldCheck } from 'lucide-react'
import { atualizarCartaoAssinatura } from '@/services/assinatura.service'
import styles from './ModalAlterarPlano.module.css'

interface ModalAtualizarCartaoProps {
    aoFechar: () => void;
    aoSucesso: () => void;
}

export function ModalAtualizarCartao({ aoFechar, aoSucesso }: ModalAtualizarCartaoProps) {
    const [numeroCartao, setNumeroCartao] = useState('')
    const [nomeTitular, setNomeTitular] = useState('')
    const [validade, setValidade] = useState('')
    const [cvv, setCvv] = useState('')
    const [salvando, setSalvando] = useState(false)
    const [erro, setErro] = useState<string | null>(null)

    const formatarNumeroCartao = (val: string) => {
        const apenasDigitos = val.replace(/\D/g, '').slice(0, 16)
        return apenasDigitos.replace(/(\d{4})/g, '$1 ').trim()
    }

    const formatarValidade = (val: string) => {
        const apenasDigitos = val.replace(/\D/g, '').slice(0, 4)
        if (apenasDigitos.length >= 2) {
            return `${apenasDigitos.slice(0, 2)}/${apenasDigitos.slice(2)}`
        }
        return apenasDigitos
    }

    const handleSalvar = async (e: React.FormEvent) => {
        e.preventDefault()
        const digitos = numeroCartao.replace(/\D/g, '')

        if (digitos.length < 15) {
            setErro('Por favor, informe um número de cartão válido.')
            return
        }
        if (!nomeTitular.trim()) {
            setErro('Informe o nome impresso no cartão.')
            return
        }
        if (validade.length < 5) {
            setErro('Informe a validade no formato MM/AA.')
            return
        }
        if (cvv.length < 3) {
            setErro('Informe o código de segurança (CVV).')
            return
        }

        setSalvando(true)
        setErro(null)
        try {
            let cardTokenFinal = `tok_dev_${digitos.slice(-4)}_${Date.now()}`
            const publicKey = process.env.NEXT_PUBLIC_MERCADOPAGO_PUBLIC_KEY

            // Em produção: tokeniza contra a API oficial do Mercado Pago
            if (publicKey && publicKey.trim() !== '') {
                const [mes, anoCurto] = validade.split('/')
                const anoCompleto = anoCurto.length === 2 ? `20${anoCurto}` : anoCurto
                const mesNum = parseInt(mes.trim(), 10)
                const anoNum = parseInt(anoCompleto.trim(), 10)

                const resp = await fetch(`https://api.mercadopago.com/v1/card_tokens?public_key=${encodeURIComponent(publicKey.trim())}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        card_number: digitos,
                        cardNumber: digitos,
                        cardholder: { name: nomeTitular.trim().toUpperCase() },
                        expiration_month: mesNum,
                        cardExpirationMonth: mesNum,
                        expiration_year: anoNum,
                        cardExpirationYear: anoNum,
                        security_code: cvv.trim(),
                        securityCode: cvv.trim(),
                    }),
                })

                if (resp.ok) {
                    const data = await resp.json()
                    if (data.id) {
                        cardTokenFinal = data.id
                    }
                } else {
                    const errData = await resp.json().catch(() => ({}))
                    const msg = errData?.message || errData?.cause?.[0]?.description || 'Dados do cartão recusados pelo Mercado Pago.'
                    throw new Error(msg)
                }
            }

            await atualizarCartaoAssinatura(cardTokenFinal)
            aoSucesso()
        } catch (err: any) {
            setErro(err.message || 'Erro ao atualizar cartão.')
        } finally {
            setSalvando(false)
        }
    }

    return (
        <div className={styles.overlay} onClick={aoFechar}>
            <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
                <div className={styles.cabecalho}>
                    <h2 className={styles.titulo}>Atualizar Cartão de Crédito</h2>
                    <button type="button" onClick={aoFechar} className={styles.btnFechar} aria-label="Fechar">
                        <X size={20} />
                    </button>
                </div>

                <form onSubmit={handleSalvar}>
                    <p className={styles.detalhesPlano} style={{ marginBottom: '16px' }}>
                        Os próximos ciclos de faturamento serão debitados neste novo cartão.
                    </p>

                    <div style={{ marginBottom: '14px' }}>
                        <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                            Número do Cartão
                        </label>
                        <div style={{ position: 'relative' }}>
                            <input
                                type="text"
                                value={numeroCartao}
                                onChange={(e) => setNumeroCartao(formatarNumeroCartao(e.target.value))}
                                placeholder="0000 0000 0000 0000"
                                style={{
                                    width: '100%',
                                    padding: '10px 14px 10px 38px',
                                    border: '1px solid var(--color-border, #e2e8f0)',
                                    borderRadius: '8px',
                                    fontSize: '0.95rem'
                                }}
                            />
                            <CreditCard size={18} style={{ position: 'absolute', left: '12px', top: '12px', color: '#94a3b8' }} />
                        </div>
                    </div>

                    <div style={{ marginBottom: '14px' }}>
                        <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                            Nome Impresso no Cartão
                        </label>
                        <input
                            type="text"
                            value={nomeTitular}
                            onChange={(e) => setNomeTitular(e.target.value.toUpperCase())}
                            placeholder="NOME COMO NO CARTÃO"
                            style={{
                                width: '100%',
                                padding: '10px 14px',
                                border: '1px solid var(--color-border, #e2e8f0)',
                                borderRadius: '8px',
                                fontSize: '0.95rem'
                            }}
                        />
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '18px' }}>
                        <div>
                            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                                Validade (MM/AA)
                            </label>
                            <input
                                type="text"
                                value={validade}
                                onChange={(e) => setValidade(formatarValidade(e.target.value))}
                                placeholder="MM/AA"
                                style={{
                                    width: '100%',
                                    padding: '10px 14px',
                                    border: '1px solid var(--color-border, #e2e8f0)',
                                    borderRadius: '8px',
                                    fontSize: '0.95rem'
                                }}
                            />
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}>
                                CVV
                            </label>
                            <input
                                type="password"
                                maxLength={4}
                                value={cvv}
                                onChange={(e) => setCvv(e.target.value.replace(/\D/g, ''))}
                                placeholder="123"
                                style={{
                                    width: '100%',
                                    padding: '10px 14px',
                                    border: '1px solid var(--color-border, #e2e8f0)',
                                    borderRadius: '8px',
                                    fontSize: '0.95rem'
                                }}
                            />
                        </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#16a34a', fontSize: '0.8rem', marginBottom: '20px' }}>
                        <ShieldCheck size={16} />
                        <span>Dados criptografados e processados diretamente via Mercado Pago</span>
                    </div>

                    {erro && <div className={styles.erro} style={{ marginBottom: '16px' }}>{erro}</div>}

                    <div className={styles.acoes}>
                        <button type="button" onClick={aoFechar} className={styles.btnCancelar} disabled={salvando}>
                            Cancelar
                        </button>
                        <button type="submit" className={styles.btnConfirmar} disabled={salvando}>
                            {salvando ? 'Salvando...' : 'Salvar Novo Cartão'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    )
}
