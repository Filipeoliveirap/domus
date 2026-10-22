'use client'

import { useState } from 'react'
import Image from 'next/image'
import { Users, Building2, UserCog, ArrowRight, Check, Share2, Copy, MessageCircle } from 'lucide-react'
import styles from './Passo3.module.css'

interface Props {
  nomeIgreja: string
  nome: string
  ehFilial?: boolean
  planoId?: string
  codigoConviteMatriz?: string | null
  irParaPessoas: () => void
  irParaPerfilIgreja: () => void
  irParaMeuPerfil: () => void
  irParaPainelInicial: () => void
}

export function Passo3({
  nomeIgreja,
  nome,
  ehFilial = false,
  planoId = 'PRO',
  codigoConviteMatriz,
  irParaPessoas,
  irParaPerfilIgreja,
  irParaMeuPerfil,
  irParaPainelInicial,
}: Props) {
  const primeiroNome = nome?.trim().split(/\s+/)[0] ?? ''
  const [copiado, setCopiado] = useState(false)

  // Filial nunca pode convidar outras congregações. Matriz convida se plano for Pro+.
  const podeConvidar = !ehFilial && planoId.toUpperCase() !== 'BASICO'
  const codigoAtivo = podeConvidar ? codigoConviteMatriz : null

  const urlConvite = codigoAtivo
    ? (typeof window !== 'undefined'
        ? `${window.location.origin}/convite/congregacao?codigo=${encodeURIComponent(codigoAtivo)}`
        : `https://domusigreja.com.br/convite/congregacao?codigo=${encodeURIComponent(codigoAtivo)}`)
    : null

  const linkWhatsapp = urlConvite
    ? `https://wa.me/?text=${encodeURIComponent(
        `Olá! Aqui está o convite para conectar nossa igreja ao Domus com plano já coberto pela ${nomeIgreja}:\n${urlConvite}`
      )}`
    : null

  const handleCopiar = async () => {
    if (!urlConvite) return
    try {
      await navigator.clipboard.writeText(urlConvite)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch {
      // Fallback
    }
  }

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

      {urlConvite && (
        <div style={{
          width: '100%',
          marginTop: '16px',
          background: 'var(--color-bg-page, #f8fafc)',
          border: '1px solid var(--color-border, #e2e8f0)',
          borderRadius: '12px',
          padding: '16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '10px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Share2 size={16} color="var(--color-primary, #2563eb)" />
            <strong style={{ fontSize: '13px', color: 'var(--color-text-primary, #0f172a)' }}>
              Convite para Igrejas da sua Rede
            </strong>
          </div>
          <p style={{ fontSize: '13px', color: 'var(--color-text-secondary, #64748b)', margin: 0, lineHeight: 1.4 }}>
            Envie este link para que suas congregações se cadastrem gratuitamente sob a cobertura da sua matriz.
          </p>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <input
              type="text"
              readOnly
              value={urlConvite}
              style={{
                flex: 1,
                padding: '8px 10px',
                fontSize: '12px',
                background: '#ffffff',
                border: '1px solid #cbd5e1',
                borderRadius: '6px',
                color: '#475569',
                fontFamily: 'monospace'
              }}
            />
            <button
              type="button"
              onClick={handleCopiar}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '4px',
                padding: '8px 12px',
                background: '#ffffff',
                border: '1px solid #cbd5e1',
                borderRadius: '6px',
                fontSize: '12px',
                fontWeight: 600,
                cursor: 'pointer'
              }}
            >
              {copiado ? <Check size={14} color="#16a34a" /> : <Copy size={14} />}
              <span>{copiado ? 'Copiado!' : 'Copiar'}</span>
            </button>
          </div>
          {linkWhatsapp && (
            <a
              href={linkWhatsapp}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '8px',
                padding: '9px 14px',
                background: '#25d366',
                color: '#ffffff',
                borderRadius: '8px',
                fontSize: '13px',
                fontWeight: 600,
                textDecoration: 'none'
              }}
            >
              <MessageCircle size={16} />
              <span>Enviar via WhatsApp</span>
            </a>
          )}
        </div>
      )}

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
