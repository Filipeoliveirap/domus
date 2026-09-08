'use client'

import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { useBalanceteAnual } from '@/hooks/financeiro/balancete/useBalanceteAnual'
import { useBalanceteFamilia } from '@/hooks/financeiro/balancete/useBalanceteFamilia'
import { BalanceteTabela } from './BalanceteTabela'
import { BalanceteCardsMes } from './BalanceteCardsMes'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import styles from './balancete.module.css'
import { SkeletonBalancete } from './SkeletonBalancete'

type Aba = 'MINHA_IGREJA' | 'CONSOLIDADO' | 'POR_CONGREGACAO'

export default function BalanceteAnualPage() {
  const [ano, setAno] = useState(new Date().getFullYear())
  const [aba, setAba] = useState<Aba>('MINHA_IGREJA')

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)

  const vinculo = useVinculoStatus(autorizado)
  const ehSede = vinculo.data?.estado === 'MAE'

  const balancetePropria = useBalanceteAnual(ano, !!autorizado && aba === 'MINHA_IGREJA')
  const balanceteFamilia = useBalanceteFamilia(ano, !!autorizado && ehSede && aba !== 'MINHA_IGREJA')
  const { congregacao } = useRotulos()

  if (!hidratado) return null
  if (!autorizado) return <AcessoRestrito />

  return (
    <div className={styles.pagina}>
      <header className={styles.header}>
        <h1 className={styles.titulo}>Balancete Anual</h1>
        <div className={styles.seletorAno}>
          <button className={styles.setaAno} aria-label="Ano anterior" onClick={() => setAno((a) => a - 1)}>‹</button>
          <strong>{ano}</strong>
          <button className={styles.setaAno} aria-label="Próximo ano" onClick={() => setAno((a) => a + 1)}>›</button>
        </div>
      </header>

      {ehSede && (
        <div role="tablist" className={styles.abas}>
          <button role="tab" aria-selected={aba === 'MINHA_IGREJA'} className={aba === 'MINHA_IGREJA' ? styles.abaAtiva : styles.aba} onClick={() => setAba('MINHA_IGREJA')}>
            Minha Igreja
          </button>
          <button role="tab" aria-selected={aba === 'CONSOLIDADO'} className={aba === 'CONSOLIDADO' ? styles.abaAtiva : styles.aba} onClick={() => setAba('CONSOLIDADO')}>
            Consolidado
          </button>
          <button role="tab" aria-selected={aba === 'POR_CONGREGACAO'} className={aba === 'POR_CONGREGACAO' ? styles.abaAtiva : styles.aba} onClick={() => setAba('POR_CONGREGACAO')}>
            Por {congregacao.singular}
          </button>
        </div>
      )}

      <Transicao key={aba} modo="fade">
        {aba === 'MINHA_IGREJA' && (
          balancetePropria.isLoading ? (
            <SkeletonBalancete />
          ) : balancetePropria.isError ? (
            <EstadoErro
              titulo="Não foi possível carregar o balancete"
              mensagem="Tente novamente."
              aoTentarNovamente={() => balancetePropria.refetch()}
            />
          ) : balancetePropria.data ? (
            <>
              <BalanceteTabela balancete={balancetePropria.data} />
              <BalanceteCardsMes balancete={balancetePropria.data} />
            </>
          ) : null
        )}

        {aba === 'CONSOLIDADO' && (
          balanceteFamilia.isLoading ? (
            <SkeletonBalancete />
          ) : balanceteFamilia.isError ? (
            <EstadoErro
              titulo="Não foi possível carregar o balancete"
              mensagem="Tente novamente."
              aoTentarNovamente={() => balanceteFamilia.refetch()}
            />
          ) : balanceteFamilia.data ? (
            <>
              <BalanceteTabela balancete={balanceteFamilia.data.consolidado} />
              <BalanceteCardsMes balancete={balanceteFamilia.data.consolidado} />
            </>
          ) : null
        )}

        {aba === 'POR_CONGREGACAO' && (
          balanceteFamilia.isLoading ? (
            <SkeletonBalancete />
          ) : balanceteFamilia.isError ? (
            <EstadoErro
              titulo="Não foi possível carregar o balancete"
              mensagem="Tente novamente."
              aoTentarNovamente={() => balanceteFamilia.refetch()}
            />
          ) : balanceteFamilia.data ? (
            <div className={styles.listaIgrejas}>
              {balanceteFamilia.data.porIgreja.map((item) => (
                <section key={item.igrejaId} className={styles.blocoIgreja}>
                  <h2>{item.nomeIgreja}{item.ehSede ? ' (Sede)' : ''}</h2>
                  <BalanceteTabela balancete={item.balancete} />
                  <BalanceteCardsMes balancete={item.balancete} />
                </section>
              ))}
            </div>
          ) : null
        )}
      </Transicao>
    </div>
  )
}
