'use client'

import Image from 'next/image'
import { Users, Building2, UserCog, ArrowRight, Check } from 'lucide-react'
import styles from './Passo3.module.css'

interface Props {
  nomeIgreja: string
  nome: string
  irParaPessoas: () => void
  irParaPerfilIgreja: () => void
  irParaMeuPerfil: () => void
  irParaPainelInicial: () => void
}

export function Passo3({
  nomeIgreja,
  nome,
  irParaPessoas,
  irParaPerfilIgreja,
  irParaMeuPerfil,
  irParaPainelInicial,
}: Props) {
  const primeiroNome = nome?.trim().split(/\s+/)[0] ?? ''

  return (
    <div className={styles.container}>
      <Image src="/images/logo2.png" alt="Domus" width={40} height={66} className={styles.logo} />
      <span className={styles.check} aria-hidden="true">
        <Check size={16} strokeWidth={3} />
      </span>

      <h2 className={styles.titulo}>Igreja criada</h2>
      <p className={styles.sub}>
        A <strong>{nomeIgreja}</strong> já está no ar.
        {primeiroNome && (
          <>
            {' '}
            Bem-vindo, <strong>{primeiroNome}</strong>.
          </>
        )}
      </p>

      <span className={styles.comecar}>POR ONDE COMEÇAR?</span>

      <div className={styles.atalhos}>
        <button
          type="button"
          className={`${styles.atalho} ${styles.atalhoPrimario}`}
          onClick={irParaPessoas}
        >
          <span className={styles.atalhoIcone}>
            <Users size={20} />
          </span>
          <span className={styles.atalhoTexto}>
            <strong>Cadastrar pessoas</strong>
            <span>Comece adicionando as pessoas da sua comunidade.</span>
          </span>
          <ArrowRight size={16} className={styles.atalhoSeta} />
        </button>

        <button type="button" className={styles.atalho} onClick={irParaPerfilIgreja}>
          <span className={styles.atalhoIcone}>
            <Building2 size={20} />
          </span>
          <span className={styles.atalhoTexto}>
            <strong>Completar perfil da igreja</strong>
            <span>Adicione informações e personalize sua igreja.</span>
          </span>
          <ArrowRight size={16} className={styles.atalhoSeta} />
        </button>

        <button type="button" className={styles.atalho} onClick={irParaMeuPerfil}>
          <span className={styles.atalhoIcone}>
            <UserCog size={20} />
          </span>
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
  )
}
