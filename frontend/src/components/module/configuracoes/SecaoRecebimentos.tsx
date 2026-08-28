'use client'

import { useState } from 'react'
import { CreditCard, CheckCircle2 } from 'lucide-react'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useConectarMercadoPago } from '@/hooks/pagamento/useConectarMercadoPago'
import { useDesconectarMercadoPago } from '@/hooks/pagamento/useDesconectarMercadoPago'
import { Button } from '@/components/common/button/Button'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import styles from './SecaoRecebimentos.module.css'

export function SecaoRecebimentos() {
  const { data, isLoading } = useContaPagamento()
  const conectar = useConectarMercadoPago()
  const desconectar = useDesconectarMercadoPago()
  const [confirmandoDesconexao, setConfirmandoDesconexao] = useState(false)

  if (isLoading) return null

  return (
    <section className={styles.wrapper}>
      <h2 className={styles.titulo}>Recebimentos</h2>
      <p className={styles.subtitulo}>
        Conecte uma conta do Mercado Pago para receber diretamente o valor das inscrições
        de eventos pagos.
      </p>

      {data?.conectada ? (
        <div className={styles.conectado}>
          <CheckCircle2 size={20} className={styles.iconeConectado} aria-hidden="true" />
          <span>Conta do Mercado Pago conectada</span>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setConfirmandoDesconexao(true)}
          >
            Desconectar
          </Button>
        </div>
      ) : (
        <div className={styles.desconectado}>
          <CreditCard size={20} aria-hidden="true" />
          <Button
            variant="primary"
            size="md"
            isLoading={conectar.isPending}
            onClick={() => conectar.mutate()}
          >
            Conectar Mercado Pago
          </Button>
        </div>
      )}

      {confirmandoDesconexao && (
        <ModalConfirmacao
          titulo="Desconectar conta do Mercado Pago?"
          mensagem="Enquanto não conectar de novo, eventos pagos desta igreja deixam de conseguir
            cobrar ninguém. Inscrições já pagas não são afetadas."
          textoConfirmar="Desconectar"
          perigo
          isLoading={desconectar.isPending}
          onConfirmar={() => desconectar.mutate(undefined, { onSuccess: () => setConfirmandoDesconexao(false) })}
          onClose={() => setConfirmandoDesconexao(false)}
        />
      )}
    </section>
  )
}
