'use client'

import { useEffect, useRef } from 'react'
import { PainelWizard, COPY_PASSO } from './PainelWizard'
import { TrocaPasso } from './TrocaPasso'
import { Passo1 } from './Passo1'
import { Passo2 } from './Passo2'
import { Passo3 } from './Passo3'
import { SecurityFooter } from './SecurityFooter'
import styles from './CadastroShell.module.css'
import type { useRegistrarIgreja } from '@/hooks/auth/UseRegistrarIgreja'

type HookRetorno = ReturnType<typeof useRegistrarIgreja>

export function CadastroShell(h: HookRetorno) {
  const totalPassos: 2 | 3 = h.googleData ? 2 : 3
  const primeiroNome = h.dadosSucesso?.nome?.trim().split(/\s+/)[0]
  const formAreaRef = useRef<HTMLDivElement>(null)

  const tituloForm =
    h.passo === 1
      ? 'Cadastre sua igreja'
      : h.passo === 2
        ? 'Crie sua conta de administrador'
        : ''
  const subtituloForm =
    h.passo === 1
      ? 'Comece pelos dados da comunidade.'
      : h.passo === 2
        ? 'Seus dados pessoais para gerenciar o Domus.'
        : ''

  // Foco no primeiro campo da cena nova, só no desktop (no mobile o foco automático abre o
  // teclado empurrando o layout antes da animação assentar — mesmo motivo do ModalMinisterioForm).
  useEffect(() => {
    if (!window.matchMedia('(min-width: 768px)').matches) return
    const t = window.setTimeout(() => {
      const alvo = formAreaRef.current?.querySelector<HTMLElement>(
        'input:not([readonly]):not([type="checkbox"]), h2',
      )
      alvo?.focus()
    }, 380)
    return () => window.clearTimeout(t)
  }, [h.passo])

  const cena =
    h.passo === 1 ? (
      <Passo1
        register={h.register}
        handleSubmit={h.handleSubmit}
        errors={h.errors}
        passo1Incompleto={h.passo1Incompleto}
        setValue={h.setValue}
        onAvancar={h.irParaPasso2}
        googleData={h.googleData}
        onGoogleAuth={h.onGoogleAuth}
        onGoogleError={h.onGoogleError}
        onSubmitGoogle={h.onSubmitGoogle}
        erroGeral={h.erroGeral}
        isLoading={h.isLoading}
        aceitouTermosGoogle={h.aceitouTermosGoogle}
        setAceitouTermosGoogle={h.setAceitouTermosGoogle}
      />
    ) : h.passo === 2 ? (
      <Passo2
        register={h.register2}
        handleSubmit={h.handleSubmit2}
        errors={h.errors2}
        passo2Incompleto={h.passo2Incompleto}
        watch={h.watch2}
        erroGeral={h.erroGeral}
        isLoading={h.isLoading}
        onSubmit={h.onSubmit}
        onVoltar={h.voltarParaPasso1}
      />
    ) : (
      <Passo3
        nomeIgreja={h.dadosSucesso?.nomeIgreja ?? ''}
        nome={h.dadosSucesso?.nome ?? ''}
        irParaPessoas={h.irParaPessoas}
        irParaPerfilIgreja={h.irParaPerfilIgreja}
        irParaMeuPerfil={h.irParaMeuPerfil}
        irParaPainelInicial={h.irParaPainelInicial}
      />
    )

  return (
    <div className={styles.page}>
      <div className={styles.shell}>
        <PainelWizard passoAtual={h.passo} totalPassos={totalPassos} primeiroNome={primeiroNome} />

        <div className={styles.formArea} ref={formAreaRef}>
          <p className={styles.passoLabel}>
            Passo {totalPassos === 2 && h.passo === 3 ? 2 : h.passo} de {totalPassos}
            {' · '}
            {COPY_PASSO[h.passo].curto}
          </p>
          {tituloForm && (
            <div className={styles.formHead}>
              <h3 className={styles.formTitulo}>{tituloForm}</h3>
              <p className={styles.formSub}>{subtituloForm}</p>
            </div>
          )}

          <TrocaPasso passo={h.passo} direcao={h.direcaoPasso}>
            {cena}
          </TrocaPasso>

          {h.passo !== 3 && <SecurityFooter />}
        </div>
      </div>
    </div>
  )
}
