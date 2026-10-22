'use client'

import React, { useState } from 'react'
import { X, CreditCard, AlertCircle, Lock } from 'lucide-react'
import { PlanoServidor } from '@/services/plano.service'
import { contratarPlanoAssinatura, ResumoAssinatura } from '@/services/assinatura.service'
import styles from './ModalAlterarPlano.module.css'

interface ModalContratarPlanoProps {
  plano: PlanoServidor
  resumo: ResumoAssinatura | null
  aoFechar: () => void
  aoSucesso: (plano: PlanoServidor) => void
}

function formatarNumeroCartao(valor: string): string {
  const v = valor.replace(/\D/g, '').slice(0, 16)
  return v.replace(/(\d{4})(?=\d)/g, '$1 ')
}

function formatarValidade(valor: string): string {
  const v = valor.replace(/\D/g, '').slice(0, 4)
  if (v.length >= 2) {
    return `${v.slice(0, 2)}/${v.slice(2)}`
  }
  return v
}

export function ModalContratarPlano({
  plano,
  resumo,
  aoFechar,
  aoSucesso,
}: ModalContratarPlanoProps) {
  const [numeroCartao, setNumeroCartao] = useState('')
  const [nomeTitular, setNomeTitular] = useState('')
  const [validade, setValidade] = useState('')
  const [cvv, setCvv] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const totalPessoas = resumo?.totalPessoas ?? 0
  const totalCongregacoes = resumo?.totalCongregacoes ?? 0
  const invalidoPorPessoas = totalPessoas > plano.limitePessoas
  const invalidoPorCongregacoes = totalCongregacoes > plano.limiteCongregacoes
  const desabilitado = invalidoPorPessoas || invalidoPorCongregacoes

  const handleSalvar = async (e: React.FormEvent) => {
    e.preventDefault()
    if (desabilitado) {
      setErro('O plano selecionado não suporta a quantidade atual de cadastros da sua igreja.')
      return
    }

    const digitos = numeroCartao.replace(/\D/g, '')
    if (digitos.length < 15) {
      setErro('Informe um número de cartão de crédito válido.')
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

      if (publicKey && publicKey.trim() !== '') {
        const [mes, anoCurto] = validade.split('/')
        const anoCompleto = anoCurto.length === 2 ? `20${anoCurto}` : anoCurto
        const mesNum = parseInt(mes.trim(), 10)
        const anoNum = parseInt(anoCompleto.trim(), 10)

        const resp = await fetch(
          `https://api.mercadopago.com/v1/card_tokens?public_key=${encodeURIComponent(publicKey.trim())}`,
          {
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
          }
        )

        if (resp.ok) {
          const data = await resp.json()
          if (data.id) {
            cardTokenFinal = data.id
          }
        } else {
          const errData = await resp.json().catch(() => ({}))
          const msg =
            errData?.message ||
            errData?.cause?.[0]?.description ||
            'Dados do cartão recusados pelo Mercado Pago.'
          throw new Error(msg)
        }
      }

      await contratarPlanoAssinatura(plano.id, cardTokenFinal)
      aoSucesso(plano)
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ||
        (err as { message?: string })?.message ||
        'Não foi possível concluir a contratação. Verifique os dados do cartão.'
      setErro(msg)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <div className={styles.overlay} onClick={aoFechar}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.cabecalho}>
          <div>
            <h2 className={styles.titulo}>Contratar {plano.nomeExibicao}</h2>
            <p className={styles.detalhesPlano} style={{ margin: '4px 0 0 0' }}>
              R$ {plano.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })} / mês
            </p>
          </div>
          <button type="button" onClick={aoFechar} className={styles.btnFechar} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        {desabilitado ? (
          <div style={{ padding: '16px 0' }}>
            <div className={styles.erro} style={{ marginBottom: '16px' }}>
              <AlertCircle size={16} aria-hidden="true" />
              <span>
                {invalidoPorPessoas
                  ? `Sua igreja possui ${totalPessoas} pessoas ativas (limite do plano: ${plano.limitePessoas}).`
                  : `Sua igreja possui ${totalCongregacoes} congregações vinculadas (limite do plano: ${plano.limiteCongregacoes}).`}
              </span>
            </div>
            <p style={{ fontSize: '13px', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Para contratar este plano, é necessário reduzir o volume de cadastros ou escolher um plano superior compatível.
            </p>
            <div className={styles.acoes} style={{ marginTop: '20px' }}>
              <button type="button" onClick={aoFechar} className={styles.btnCancelar}>
                Escolher outro plano
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSalvar} style={{ marginTop: '16px' }}>
            <div style={{ marginBottom: '14px' }}>
              <label
                htmlFor="input-num-cartao"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}
              >
                Número do Cartão
              </label>
              <div style={{ position: 'relative' }}>
                <input
                  id="input-num-cartao"
                  type="text"
                  value={numeroCartao}
                  onChange={(e) => setNumeroCartao(formatarNumeroCartao(e.target.value))}
                  placeholder="0000 0000 0000 0000"
                  required
                  style={{
                    width: '100%',
                    padding: '10px 14px 10px 38px',
                    border: '1px solid var(--color-border, #e2e8f0)',
                    borderRadius: '8px',
                    fontSize: '0.95rem',
                  }}
                />
                <CreditCard
                  size={18}
                  style={{ position: 'absolute', left: '12px', top: '12px', color: '#94a3b8' }}
                />
              </div>
            </div>

            <div style={{ marginBottom: '14px' }}>
              <label
                htmlFor="input-titular-cartao"
                style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}
              >
                Nome Impresso no Cartão
              </label>
              <input
                id="input-titular-cartao"
                type="text"
                value={nomeTitular}
                onChange={(e) => setNomeTitular(e.target.value.toUpperCase())}
                placeholder="NOME COMO NO CARTÃO"
                required
                style={{
                  width: '100%',
                  padding: '10px 14px',
                  border: '1px solid var(--color-border, #e2e8f0)',
                  borderRadius: '8px',
                  fontSize: '0.95rem',
                }}
              />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '18px' }}>
              <div>
                <label
                  htmlFor="input-validade-cartao"
                  style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}
                >
                  Validade (MM/AA)
                </label>
                <input
                  id="input-validade-cartao"
                  type="text"
                  value={validade}
                  onChange={(e) => setValidade(formatarValidade(e.target.value))}
                  placeholder="MM/AA"
                  required
                  style={{
                    width: '100%',
                    padding: '10px 14px',
                    border: '1px solid var(--color-border, #e2e8f0)',
                    borderRadius: '8px',
                    fontSize: '0.95rem',
                  }}
                />
              </div>

              <div>
                <label
                  htmlFor="input-cvv-cartao"
                  style={{ display: 'block', fontSize: '0.875rem', fontWeight: 600, marginBottom: '6px' }}
                >
                  CVV
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    id="input-cvv-cartao"
                    type="password"
                    maxLength={4}
                    value={cvv}
                    onChange={(e) => setCvv(e.target.value.replace(/\D/g, ''))}
                    placeholder="123"
                    required
                    style={{
                      width: '100%',
                      padding: '10px 14px 10px 34px',
                      border: '1px solid var(--color-border, #e2e8f0)',
                      borderRadius: '8px',
                      fontSize: '0.95rem',
                    }}
                  />
                  <Lock
                    size={16}
                    style={{ position: 'absolute', left: '10px', top: '13px', color: '#94a3b8' }}
                  />
                </div>
              </div>
            </div>

            {erro && <div className={styles.erro} style={{ marginBottom: '14px' }}>{erro}</div>}

            <div className={styles.acoes}>
              <button
                type="button"
                onClick={aoFechar}
                className={styles.btnCancelar}
                disabled={salvando}
              >
                Cancelar
              </button>
              <button
                type="submit"
                className={styles.btnConfirmar}
                disabled={salvando}
              >
                {salvando ? 'Processando pagamento…' : `Confirmar Assinatura (R$ ${plano.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}/mês)`}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
