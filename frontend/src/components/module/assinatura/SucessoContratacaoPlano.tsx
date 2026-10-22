'use client'

import React from 'react'
import Link from 'next/link'
import { CheckCircle2, ArrowRight } from 'lucide-react'
import { PlanoServidor } from '@/services/plano.service'
import styles from './SucessoContratacaoPlano.module.css'

interface Props {
  plano: PlanoServidor
  nomeIgreja?: string | null
  modo?: 'CONTRATACAO' | 'UPGRADE'
}

export function SucessoContratacaoPlano({ plano, nomeIgreja, modo = 'CONTRATACAO' }: Props) {
  const ehUpgrade = modo === 'UPGRADE'

  return (
    <div className={styles.container}>
      <div className={styles.card}>
        <div className={styles.aneisAprovado}>
          <div className={styles.anelAprovado} aria-hidden="true" />
          <CheckCircle2 size={46} strokeWidth={2.2} className={styles.iconeAprovado} />
        </div>

        <h1 className={styles.titulo}>
          {ehUpgrade ? 'Plano Atualizado com Sucesso!' : 'Assinatura Confirmada!'}
        </h1>
        <p className={styles.subtitulo}>
          {ehUpgrade
            ? 'Sua igreja foi migrada para o novo plano. A nova capacidade e os recursos já estão ativos.'
            : 'Seu plano agora está ativo e todas as funcionalidades de edição estão liberadas.'
          }
        </p>

        <div className={styles.resumoBox}>
          {nomeIgreja && (
            <div className={styles.resumoLinha}>
              <span className={styles.resumoLabel}>Igreja</span>
              <span className={styles.resumoValor}>{nomeIgreja}</span>
            </div>
          )}
          <div className={styles.resumoLinha}>
            <span className={styles.resumoLabel}>{ehUpgrade ? 'Novo Plano' : 'Plano Contratado'}</span>
            <span className={styles.resumoValor}>{plano.nomeExibicao}</span>
          </div>
          <div className={styles.resumoLinha}>
            <span className={styles.resumoLabel}>Valor Mensal</span>
            <span className={styles.resumoValor}>
              R$ {plano.valorMensal.toLocaleString('pt-BR', { minimumFractionDigits: 2 })} / mês
            </span>
          </div>
          <div className={styles.resumoLinha}>
            <span className={styles.resumoLabel}>Status</span>
            <span className={styles.resumoAtivo}>{ehUpgrade ? 'Plano Ativo' : 'Ativo • Em dia'}</span>
          </div>
        </div>

        <Link href="/inicio" className={styles.btnInicio}>
          <span>Ir para o painel inicial</span>
          <ArrowRight size={16} />
        </Link>
      </div>
    </div>
  )
}
