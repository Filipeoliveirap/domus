'use client'

import Image from 'next/image'
import styles from './PainelWizard.module.css'

export const COPY_PASSO: Record<
  1 | 2 | 3 | 4,
  { rotulo: string; headline: string; texto: string; curto: string }
> = {
  1: {
    rotulo: 'Vamos começar',
    headline: 'Leva menos de 2 minutos',
    texto: 'Cadastre sua igreja e crie sua conta de administrador.',
    curto: 'Dados da igreja',
  },
  2: {
    rotulo: 'Falta pouco',
    headline: 'Sua conta de administrador',
    texto: 'Só mais alguns dados e o Domus é seu.',
    curto: 'Sua conta de admin',
  },
  3: {
    rotulo: 'Ativação',
    headline: 'Período de testes gratuito',
    texto: '14 dias para experimentar todos os recursos sem cobrança imediata.',
    curto: 'Ativação do plano',
  },
  4: {
    rotulo: 'Pronto',
    headline: 'Tudo certo',
    texto: 'Sua igreja foi criada. Escolha por onde começar.',
    curto: 'Tudo pronto',
  },
}

interface Props {
  passoAtual: 1 | 2 | 3 | 4
  totalPassos: 2 | 3 | 4
  primeiroNome?: string
}

export function PainelWizard({ passoAtual, totalPassos, primeiroNome }: Props) {
  const copy = COPY_PASSO[passoAtual]
  const headline =
    passoAtual === 4 && primeiroNome ? `Tudo certo, ${primeiroNome}` : copy.headline

  const passoExibido = totalPassos === 2 && passoAtual === 4 ? 2 : passoAtual
  const numeros = Array.from({ length: totalPassos }, (_, i) => i + 1)

  return (
    <aside
      className={`${styles.painel} ${passoAtual === 4 ? styles.celebra : ''}`}
      role="group"
      aria-label={`Passo ${passoExibido} de ${totalPassos}`}
    >
      <Image
        src="/images/sanctuary.jpg"
        alt=""
        aria-hidden="true"
        className={styles.foto}
        width={520}
        height={900}
        priority
      />
      <div className={styles.veu} aria-hidden="true" />

      <div className={styles.conteudo}>
        <div className={styles.marca}>
          <Image
            src="/images/logo2.png"
            alt="Domus"
            width={26}
            height={44}
            className={styles.logo}
          />
          <span className={styles.marcaNome}>DOMUS</span>
          <span className={styles.passoMobile}>
            Passo {passoExibido} de {totalPassos}
          </span>
        </div>

        <div className={styles.meio}>
          <p className={styles.rotulo}>{copy.rotulo}</p>
          <h2 className={styles.headline}>{headline}</h2>
          <p className={styles.texto}>{copy.texto}</p>
        </div>

        <ol className={styles.progresso}>
          {numeros.map((n) => {
            const feito = n < passoExibido
            const atual = n === passoExibido
            return (
              <li key={n} className={styles.progItem}>
                <span
                  className={`${styles.progNum} ${feito ? styles.progFeito : ''} ${
                    atual ? styles.progAtual : ''
                  }`}
                  aria-current={atual ? 'step' : undefined}
                >
                  {feito ? '✓' : n}
                </span>
                <span className={`${styles.progLinha} ${atual ? styles.progLinhaAtual : ''}`}>
                  <i style={{ width: feito ? '100%' : '0%' }} />
                </span>
              </li>
            )
          })}
        </ol>
      </div>
    </aside>
  )
}
