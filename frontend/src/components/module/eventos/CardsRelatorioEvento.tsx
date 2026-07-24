'use client'

import type { RelatorioEventoResponse } from '@/types/evento.type'
import styles from './CardsRelatorioEvento.module.css'

interface Props {
  relatorio: RelatorioEventoResponse
}

/**
 * Relatório individual (página de inscritos). Os cards "Inscritos" e "Composição de
 * Inscritos" aparecem SEMPRE que há inscrição — não dependem de controle de presença. Os
 * outros 3 (Presença Total, Composição de Presença, Impacto Global) só aparecem quando o
 * evento controla presença (`relatorio.compareceram !== null` — a página que chama já
 * garante isso condicionando a renderização, mas o componente também se defende sozinho).
 *
 * "Pessoas da Igreja" / "Convidados": rótulo de TELA — nunca "Membros"/"Visitantes", que
 * colidiriam com o enum de vínculo do domínio.
 */
export function CardsRelatorioEvento({ relatorio }: Props) {
  const totalInscritos = relatorio.inscritos.pessoas + relatorio.inscritos.convidados

  return (
    <div className={styles.grade}>
      <div className={styles.card}>
        <span className={styles.cardTitulo}>Inscritos</span>
        <div className={styles.presencaLinha}>
          <div
            className={styles.circulo}
            style={{ '--pct': relatorio.percentualIgrejaInscritos } as React.CSSProperties}
          >
            <span className={styles.circuloTexto}>{relatorio.percentualIgrejaInscritos}%</span>
          </div>
          <div>
            <p className={styles.presencaValor}>{relatorio.inscritos.pessoas} pessoas</p>
            <p className={styles.presencaLabel}>da igreja inscritas</p>
          </div>
        </div>
      </div>

      <div className={styles.card}>
        <span className={styles.cardTitulo}>Composição de Inscritos</span>
        <div className={styles.composicaoLinha}>
          <span className={styles.composicaoValor}>{relatorio.inscritos.pessoas}</span>
          <span className={styles.composicaoLabel}>Pessoas da Igreja</span>
        </div>
        <div className={styles.composicaoLinha}>
          <span className={styles.composicaoValor}>{relatorio.inscritos.convidados}</span>
          <span className={styles.composicaoLabel}>Convidados</span>
        </div>
      </div>

      {relatorio.compareceram && (() => {
        const totalCompareceram = relatorio.compareceram.pessoas + relatorio.compareceram.convidados
        const percentualPresenca = totalInscritos > 0
          ? Math.round((totalCompareceram / totalInscritos) * 1000) / 10
          : 0

        return (
          <>
            <div className={styles.card}>
              <span className={styles.cardTitulo}>Presença Total</span>
              <div className={styles.presencaLinha}>
                <div
                  className={styles.circulo}
                  style={{ '--pct': percentualPresenca } as React.CSSProperties}
                >
                  <span className={styles.circuloTexto}>{percentualPresenca}%</span>
                </div>
                <div>
                  <p className={styles.presencaValor}>{totalCompareceram} de {totalInscritos}</p>
                  <p className={styles.presencaLabel}>compareceram</p>
                </div>
              </div>
            </div>

            <div className={styles.card}>
              <span className={styles.cardTitulo}>Composição de Presença</span>
              <div className={styles.composicaoLinha}>
                <span className={styles.composicaoValor}>{relatorio.compareceram.pessoas}</span>
                <span className={styles.composicaoLabel}>Pessoas da Igreja</span>
              </div>
              <div className={styles.composicaoLinha}>
                <span className={styles.composicaoValor}>{relatorio.compareceram.convidados}</span>
                <span className={styles.composicaoLabel}>Convidados</span>
              </div>
            </div>

            <div className={styles.card}>
              <span className={styles.cardTitulo}>Impacto Global</span>
              <p className={styles.impactoValor}>{relatorio.percentualIgreja ?? 0}%</p>
              <p className={styles.impactoLabel}>da igreja compareceu a este evento</p>
            </div>
          </>
        )
      })()}
    </div>
  )
}
