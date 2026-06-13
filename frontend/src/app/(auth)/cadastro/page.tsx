'use client'

import { useRegistrarIgreja } from '../../../hooks/auth/UseRegistrarIgreja'
import { SanctuaryPanel } from './SanctuaryPanel'
import { ProgressIndicator } from './ProgressIndicator'
import { Passo1 } from './Passo1'
import { Passo2 } from './Passo2'
import { SecurityFooter } from './SecurityFooter'
import styles from './Page.module.css'
import Image from 'next/image'

export default function CadastroPage() {
  const {
    passo,
    irParaPasso2,
    voltarParaPasso1,
    register, handleSubmit,setValue, errors, passo1Incompleto,
    register2, handleSubmit2, errors2, passo2Incompleto, watch2,
    erroGeral, isLoading, onSubmit,
  } = useRegistrarIgreja()

  // ─── PASSO 1 — layout com card 2 colunas + SanctuaryPanel ─────────
  if (passo === 1) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <SanctuaryPanel />
          <div className={styles.formPanel}>
            <ProgressIndicator passoAtual={1} totalPassos={2} />
            <Passo1
              register={register}
              handleSubmit={handleSubmit}
              errors={errors}
              passo1Incompleto={passo1Incompleto}
              setValue={setValue}
              onAvancar={irParaPasso2}
            />
            <SecurityFooter />
          </div>
        </div>
      </div>
    )
  }

  // ─── PASSO 2 — layout full screen com gradiente ───────────────────
  return (
    <div className={styles.page2}>

      {/* Lado esquerdo — texto livre */}
      <div className={styles.leftSide}>
        <div className={styles.brandRow}>
          <Image src="/images/logo2.png" alt="domus" width={40} height={60}/>
          
          <span className={styles.brandName}>Domus</span>
        </div>

        <h1 className={styles.leftHeadline}>
          O espaço digital para sua comunidade crescer.
        </h1>

        <p className={styles.leftSubtitle}>
          Gerencie ministérios, finanças e membros com a serenidade de um sistema
          desenhado para servir.
        </p>
      </div>

      {/* Lado direito — card do formulário */}
      <div className={styles.rightSide}>
        <div className={styles.formCard}>

          {/* Header do card com indicador de progresso */}
          <div className={styles.cardHeader}>
            <div className={styles.cardHeaderTop}>
              <span className={styles.cardHeaderLabel}>PASSO 2 DE 2</span>
              <span className={styles.cardHeaderRight}>Configuração de Perfil</span>
            </div>
            <div className={styles.cardProgressBars}>
              <div className={styles.cardProgressBar} />
              <div className={styles.cardProgressBar} />
            </div>
          </div>

          {/* Formulário do passo 2 */}
          <Passo2
            register={register2}
            handleSubmit={handleSubmit2}
            errors={errors2}
            passo2Incompleto={passo2Incompleto}
            watch={watch2}
            erroGeral={erroGeral}
            isLoading={isLoading}
            onSubmit={onSubmit}
            onVoltar={voltarParaPasso1}
          />

        </div>
      </div>

    </div>
  )
}