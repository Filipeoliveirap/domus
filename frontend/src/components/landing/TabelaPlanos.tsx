'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { buscarPlanosServidor, PlanoServidor } from '@/services/plano.service'
import styles from '@/app/(public)/planos/planos.module.css'

export default function TabelaPlanos() {
  const [planos, setPlanos] = useState<PlanoServidor[]>([])
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    buscarPlanosServidor().then((res) => {
      setPlanos(res)
      setCarregando(false)
    })
  }, [])

  if (carregando) {
    return (
      <div style={{ textAlign: 'center', padding: '48px', color: 'var(--color-text-secondary)' }}>
        Carregando catálogo de planos...
      </div>
    )
  }

  return (
    <div className={styles.gridPlanos}>
      {planos.map((p) => {
        const popular = p.id === 'PRO'
        return (
          <div
            key={p.id}
            className={`${styles.cardPlano} ${popular ? styles.cardPopular : ''}`}
          >
            {popular && <span className={styles.badgePopular}>Mais Popular</span>}

            <div>
              <h2 className={styles.nomePlano}>{p.nomeExibicao}</h2>
              <p className={styles.limitesText}>
                Até {p.limitePessoas === 99999 ? 'Ilimitadas' : `${p.limitePessoas}`} pessoas •{' '}
                {p.limiteCongregacoes === 0 ? 'Sem congregações' : `${p.limiteCongregacoes} congregações`}
              </p>

              <div className={styles.precoBox}>
                <span className={styles.cifrao}>R$</span>
                <span className={styles.valor}>{p.valorMensal}</span>
                <span className={styles.periodo}>/mês</span>
              </div>

              <hr className={styles.divisoria} />

              <h3 className={styles.tituloFeatures}>Recursos Incluídos:</h3>
              <div className={styles.listaFeatures}>
                {p.descricoesFeaturesHabilitadas.map((desc, i) => (
                  <div key={i} className={styles.itemFeature}>
                    <span className={styles.iconeCheck}>✓</span>
                    <span>{desc}</span>
                  </div>
                ))}
              </div>
            </div>

            <Link
              href={`/cadastro?plano=${p.id}`}
              className={`${styles.btnAssinar} ${popular ? styles.btnPrimary : styles.btnSecondary}`}
            >
              Testar 14 dias grátis
            </Link>
          </div>
        )
      })}
    </div>
  )
}
