'use client'

import { useEffect, useRef, useState } from 'react'
import { initMercadoPago, Payment } from '@mercadopago/sdk-react'
import { notificar } from '@/components/common/Notificacao/notificar'
import { cobrancaService } from '@/services/cobranca.service'
import styles from './PaymentBrickCheckout.module.css'

interface Props {
  cobrancaId: string
  valor: number
  /** E-mail do pagador — o Brick aceita o campo vazio e deixa a pessoa preencher, mas
   *  pré-preencher com o e-mail já conhecido (titular logado) evita retrabalho. */
  emailPagador?: string
  onPagamentoCriado: (mpPaymentId: string) => void
}

let inicializado = false

/**
 * Payment Brick embutido (PIX + cartão na mesma tela) — decisão do brainstorm de não
 * redirecionar pro Mercado Pago ("fica amador"). O tokenizador de cartão roda no
 * navegador via SDK do Mercado Pago; o dado de cartão nunca passa pelo backend do Domus,
 * só o token já gerado (`formData.token`).
 *
 * <p>Campos usados de `formData` (confirmados no `.d.ts` do pacote instalado,
 * `node_modules/@mercadopago/sdk-react/esm/bricks/cardPayment/type.d.ts`):
 * `token`, `payment_method_id`, `installments`, `payer.email` — repassados quase iguais
 * pro backend em {@code POST /cobrancas/{id}/pagar} (ver CobrancaController, Task 14).
 * PIX não gera `token`/`installments` (o Brick manda `undefined`) — o backend aceita os
 * dois nulos nesse caso.</p>
 */
export function PaymentBrickCheckout({ cobrancaId, valor, emailPagador, onPagamentoCriado }: Props) {
  const publicKeyRef = useRef(process.env.NEXT_PUBLIC_MERCADOPAGO_PUBLIC_KEY ?? '')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    if (!inicializado && publicKeyRef.current) {
      initMercadoPago(publicKeyRef.current, { locale: 'pt-BR' })
      inicializado = true
    }
  }, [])

  return (
    // Critical 5 (revisão final de branch): o Brick não expõe uma prop "disabled" — a
    // defesa real contra clique duplo é no backend (idempotência via mpPaymentId em
    // CobrancaController.pagar), mas aqui soma-se uma camada de UX: `pointerEvents: none`
    // bloqueia clique novo enquanto `enviando`, e o guard logo no início do onSubmit
    // recusa uma segunda submissão que já esteja "em voo" (o SDK poderia disparar de novo
    // antes do clique ser bloqueado visualmente).
    <div className={styles.wrapper} style={enviando ? { pointerEvents: 'none', opacity: 0.6 } : undefined}>
      <Payment
        initialization={{ amount: valor, payer: { email: emailPagador } }}
        customization={{ paymentMethods: { bankTransfer: 'all', creditCard: 'all' } }}
        onSubmit={async ({ formData }) => {
          if (enviando) return
          setEnviando(true)
          try {
            const resposta = await cobrancaService.pagar(cobrancaId, {
              token: formData.token ?? null,
              paymentMethodId: formData.payment_method_id,
              installments: formData.installments ?? null,
              payerEmail: formData.payer?.email ?? emailPagador ?? '',
            })
            onPagamentoCriado(resposta.mpPaymentId)
          } catch {
            notificar.erro(
              'Não foi possível processar o pagamento',
              'Confira os dados e tente novamente. Se o problema continuar, tente outro meio de pagamento.'
            )
          } finally {
            setEnviando(false)
          }
        }}
        onError={(erro) => {
          console.error('Erro no Payment Brick', erro)
          notificar.erro('Erro no checkout', 'Não foi possível carregar o formulário de pagamento.')
        }}
      />
      {enviando && <p className={styles.processando}>Processando pagamento…</p>}
    </div>
  )
}
