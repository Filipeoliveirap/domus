'use client'

import React, { useState, useEffect } from 'react'
import { ArrowLeft, ArrowRight, ShieldCheck, CheckCircle2, Lock, CreditCard, Info } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { buscarPlanosServidor, PlanoServidor } from '@/services/plano.service'
import styles from './Passo3Checkout.module.css'

interface Props {
  planoId?: string
  codigoConvite?: string | null
  matrizNome?: string | null
  isLoading: boolean
  erroGeral: string | null
  onSubmit: (cardTokenId?: string) => Promise<void>
  onVoltar: () => void
}

function formatarValidade(valor: string): string {
  const v = valor.replace(/\D/g, '').slice(0, 4)
  if (v.length >= 2) {
    return `${v.slice(0, 2)}/${v.slice(2)}`
  }
  return v
}

function formatarNumeroCartao(valor: string): string {
  const v = valor.replace(/\D/g, '').slice(0, 16)
  return v.replace(/(\d{4})(?=\d)/g, '$1 ')
}

export function Passo3Checkout({
  planoId = 'PRO',
  codigoConvite,
  matrizNome,
  isLoading,
  erroGeral,
  onSubmit,
  onVoltar,
}: Props) {
  const [numeroCartao, setNumeroCartao] = useState('')
  const [nomeTitular, setNomeTitular] = useState('')
  const [validade, setValidade] = useState('')
  const [cvv, setCvv] = useState('')
  const [planos, setPlanos] = useState<PlanoServidor[]>([])

  useEffect(() => {
    buscarPlanosServidor().then(setPlanos).catch(() => undefined)
  }, [])

  const ehFilhaComConvite = Boolean(codigoConvite)
  const planoEncontrado = planos.find((p) => p.id.toUpperCase() === planoId.toUpperCase())

  // Resolução acoplada diretamente à fonte da verdade (backend)
  const nomePlano = planoEncontrado?.nomeExibicao ?? (planoId.toUpperCase() === 'BASICO' ? 'Básico' : planoId.toUpperCase() === 'PRO_PLUS' ? 'Pro+' : planoId.toUpperCase() === 'ENTERPRISE' ? 'Enterprise' : 'Pro')
  const valorPlano = planoEncontrado
    ? planoEncontrado.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })
    : (planoId.toUpperCase() === 'BASICO' ? '49,00' : planoId.toUpperCase() === 'PRO_PLUS' ? '249,00' : planoId.toUpperCase() === 'ENTERPRISE' ? '449,00' : '129,00')

  const handleSubmeter = async (e: React.FormEvent) => {
    e.preventDefault()
    if (ehFilhaComConvite) {
      await onSubmit()
    } else {
      const digitos = numeroCartao.replace(/\D/g, '')
      if (digitos.length < 15) {
        alert('Por favor, informe um número de cartão de crédito válido.')
        return
      }
      if (!nomeTitular.trim()) {
        alert('Informe o nome impresso no cartão.')
        return
      }
      if (validade.length < 5) {
        alert('Informe a validade no formato MM/AA.')
        return
      }
      if (cvv.length < 3) {
        alert('Informe o código de segurança (CVV).')
        return
      }

      let cardTokenFinal = `tok_dev_${digitos.slice(-4)}_${Date.now()}`
      const publicKey = process.env.NEXT_PUBLIC_MERCADOPAGO_PUBLIC_KEY

      // Em produção: tokeniza contra a API oficial do Mercado Pago
      if (publicKey && publicKey.trim() !== '') {
        try {
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
            alert(msg)
            return
          }
        } catch {
          // Se falha de rede/CORS no browser, segue com token
        }
      }

      await onSubmit(cardTokenFinal)
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.cardResumo}>
        <div className={styles.resumoHeader}>
          <h3 className={styles.planoNome}>
            {ehFilhaComConvite ? 'Plano Coberto pela Matriz' : `Plano ${nomePlano}`}
          </h3>
          <span className={ehFilhaComConvite ? styles.badgeIsento : styles.badgeTrial}>
            {ehFilhaComConvite ? 'Isento de cobrança' : '14 dias grátis'}
          </span>
        </div>

        {ehFilhaComConvite ? (
          <div className={styles.cardVinculo}>
            <CheckCircle2 size={24} className={styles.iconeVinculo} />
            <div className={styles.textoVinculo}>
              Sua igreja fará parte da <strong>{matrizNome || 'Igreja Matriz'}</strong>. O plano de assinatura já está totalmente coberto.
            </div>
          </div>
        ) : (
          <>
            <div className={styles.precoLinha}>
              <span className={styles.cifrao}>R$</span>
              <span className={styles.precoValor}>{valorPlano}</span>
              <span className={styles.precoPeriodo}>/mês após período de testes</span>
            </div>

            <div className={styles.avisoTrial}>
              <Info size={18} className={styles.avisoIcone} />
              <span>
                <strong>Sem cobrança imediata:</strong> você terá 14 dias de acesso completo sem pagar nada. Cancele com um clique antes do prazo para não ser cobrado.
              </span>
            </div>
          </>
        )}
      </div>

      <form className={styles.formCartao} onSubmit={handleSubmeter}>
        {!ehFilhaComConvite && (
          <>
            <span className={styles.secaoTitulo}>Dados do Cartão de Crédito para Ativação do Trial</span>

            <Input
              id="numeroCartao"
              label="NÚMERO DO CARTÃO"
              placeholder="0000 0000 0000 0000"
              maxLength={19}
              value={numeroCartao}
              onChange={(e) => setNumeroCartao(formatarNumeroCartao(e.target.value))}
              leftIcon={<CreditCard size={16} />}
              required
            />

            <Input
              id="nomeTitular"
              label="NOME IMPRESSO NO CARTÃO"
              placeholder="Ex: JOAO S OLIVEIRA"
              value={nomeTitular}
              onChange={(e) => setNomeTitular(e.target.value.toUpperCase())}
              required
            />

            <div className={styles.grid2}>
              <Input
                id="validadeCartao"
                label="VALIDADE"
                placeholder="MM/AA"
                maxLength={5}
                value={validade}
                onChange={(e) => setValidade(formatarValidade(e.target.value))}
                required
              />

              <Input
                id="cvvCartao"
                label="CVV"
                placeholder="123"
                maxLength={4}
                value={cvv}
                onChange={(e) => setCvv(e.target.value.replace(/\D/g, '').slice(0, 4))}
                leftIcon={<Lock size={16} />}
                required
              />
            </div>
          </>
        )}

        {erroGeral && (
          <div className={styles.avisoTrial} style={{ borderColor: 'var(--color-danger)', color: 'var(--color-danger)' }}>
            {erroGeral}
          </div>
        )}

        <div className={styles.acoes}>
          <button type="button" onClick={onVoltar} className={styles.btnVoltar} disabled={isLoading}>
            <ArrowLeft size={16} />
            <span>Voltar</span>
          </button>

          <Button type="submit" variant="primary" size="lg" isLoading={isLoading} disabled={isLoading}>
            <span>{ehFilhaComConvite ? 'Concluir cadastro gratuitamente' : 'Começar 14 dias grátis'}</span>
            <ArrowRight size={16} />
          </Button>
        </div>
      </form>
    </div>
  )
}
