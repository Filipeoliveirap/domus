'use client'

import { useRegistrarIgreja } from '../../../hooks/auth/UseRegistrarIgreja'
import { SanctuaryPanel } from './SanctuaryPanel'
import { ProgressIndicator } from './ProgressIndicator'
import { Passo1 } from './Passo1'
import { Passo2 } from './Passo2'
import { SecurityFooter } from './SecurityFooter'
import styles from './Page.module.css'
import Image from 'next/image'
import { Check, Users, Building2, UserCog, ArrowRight } from 'lucide-react'

export default function CadastroPage() {
  const {
    passo,
    irParaPasso2,
    voltarParaPasso1,
    register, handleSubmit, setValue, errors, passo1Incompleto,
    register2, handleSubmit2, errors2, passo2Incompleto, watch2,
    erroGeral, isLoading, onSubmit,
    googleData, onGoogleAuth, onGoogleError, onSubmitGoogle,
    aceitouTermosGoogle, setAceitouTermosGoogle,
    dadosSucesso, irParaPessoas, irParaPerfilIgreja, irParaMeuPerfil,
      irParaPainelInicial,
  } = useRegistrarIgreja()

  // ─── PASSO 1 ──────────────────────────────────────────────────────
  if (passo === 1) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <SanctuaryPanel />
          <div className={styles.formPanel}>
            <ProgressIndicator passoAtual={1} totalPassos={googleData ? 2 : 3} />
            <Passo1
              register={register}
              handleSubmit={handleSubmit}
              errors={errors}
              passo1Incompleto={passo1Incompleto}
              setValue={setValue}
              onAvancar={irParaPasso2}
              googleData={googleData}
              onGoogleAuth={onGoogleAuth}
              onGoogleError={onGoogleError}
              onSubmitGoogle={onSubmitGoogle}
              erroGeral={erroGeral}
              isLoading={isLoading}
              aceitouTermosGoogle={aceitouTermosGoogle}
              setAceitouTermosGoogle={setAceitouTermosGoogle}
            />
            <SecurityFooter />
          </div>
        </div>
      </div>
    )
  }

  // ─── PASSO 3 — boas-vindas ────────────────────────────────────────
  if (passo === 3 && dadosSucesso?.nome) {
    return (
      <div className={styles.pageSucesso}>
        <div className={styles.cardSucesso}>
          <ProgressIndicator passoAtual={googleData ? 2 : 3} totalPassos={googleData ? 2 : 3} />

          <div className={styles.sucessoIcone}>
            <Check size={32} strokeWidth={3} />
          </div>

          <h1 className={styles.sucessoTitulo}>
            Bem-vindo ao Domus, {dadosSucesso.nome?.split(' ')[0] ?? ''}!
          </h1>
          <p className={styles.sucessoSubtitulo}>
            A igreja <strong>{dadosSucesso.nomeIgreja}</strong> foi criada com sucesso.
            Sua conta de administrador já está pronta.
          </p>
          <p className={styles.sucessoNota}>
            Você pode completar o perfil da sua igreja e os demais dados quando quiser,
            dentro do sistema.
          </p>

          <span className={styles.sucessoComecar}>POR ONDE COMEÇAR?</span>

          <div className={styles.atalhos}>
            <button
              type="button"
              className={`${styles.atalho} ${styles.atalhoPrimario}`}
              onClick={irParaPessoas}
            >
              <span className={styles.atalhoIcone}><Users size={20} /></span>
              <span className={styles.atalhoTexto}>
                <strong>Cadastrar pessoas</strong>
                <span>Comece adicionando as pessoas da sua comunidade.</span>
              </span>
              <ArrowRight size={16} className={styles.atalhoSeta} />
            </button>

            <button type="button" className={styles.atalho} onClick={irParaPerfilIgreja}>
              <span className={styles.atalhoIcone}><Building2 size={20} /></span>
              <span className={styles.atalhoTexto}>
                <strong>Completar perfil da igreja</strong>
                <span>Adicione informações e personalize sua igreja.</span>
              </span>
              <ArrowRight size={16} className={styles.atalhoSeta} />
            </button>

            <button type="button" className={styles.atalho} onClick={irParaMeuPerfil}>
              <span className={styles.atalhoIcone}><UserCog size={20} /></span>
              <span className={styles.atalhoTexto}>
                <strong>Completar meu perfil</strong>
                <span>Adicione seus dados pessoais e de contato.</span>
              </span>
              <ArrowRight size={16} className={styles.atalhoSeta} />
            </button>
          </div>

          <button type="button" className={styles.pularLink} onClick={irParaPainelInicial}>
            Pular e ir para o painel inicial
          </button>
        </div>
      </div>
    )
  }

  // ─── PASSO 2 ──────────────────────────────────────────────────────
  return (
    <div className={styles.page2}>
      <div className={styles.leftSide}>
        <div className={styles.brandRow}>
          <Image src="/images/logo2.png" alt="Domus" width={40} height={60} />
          {/* Mesma assinatura do painel do santuário — a marca fala igual nas duas telas. */}
          <span className={styles.brandName}>
            DOMUS
            <span className={styles.brandTagline}>Gestão Eclesiástica</span>
          </span>
        </div>

        <h1 className={styles.leftHeadline}>
          O espaço digital para sua comunidade crescer.
        </h1>

        <p className={styles.leftSubtitle}>
          Gerencie ministérios, finanças e membros com a serenidade de um sistema
          desenhado para servir.
        </p>
      </div>

      <div className={styles.rightSide}>
        <div className={styles.formCard}>
          <ProgressIndicator passoAtual={2} totalPassos={3} labelDireita="Configuração de Perfil" />

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