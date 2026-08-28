'use client'

import Link from 'next/link'
import { Eye, EyeOff, Mail, Lock } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { GoogleLogin } from '@react-oauth/google'
import { useLogin } from '@/hooks/auth/useLogin'
import { authService } from '@/services/auth.service'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './page.module.css'
import Image from 'next/image'

export default function LoginPage() {
  const [mostrarSenha, setMostrarSenha] = useState(false)
  // O <GoogleLogin> renderiza um iframe de largura FIXA — 340px estourava em telas de
  // ~320px. Medimos o container e passamos a largura real (a API do Google aceita 200–400).
  const googleWrapRef = useRef<HTMLDivElement>(null)
  const [larguraGoogle, setLarguraGoogle] = useState(340)
  const {
    register, handleSubmit, errors, erroGeral, isLoading, isButtonDisabled, onSubmit,
    onGoogleLogin, onGoogleError, contaSemSenha, emailDigitado, aplicarSessao,
  } = useLogin()

  // Quem já tem sessão válida (cookie httpOnly ainda ativo) e cai em /login — por um link
  // de e-mail com ?next=, por exemplo — não deve ver o formulário: só passa direto pro
  // destino. Fica só nesta página (não no hook) porque /reset-password também usa
  // useLogin() só pelo botão do Google, e não pode herdar este redirecionamento —
  // senão o link de convite de um novo admin cairia na sessão de quem já está logado.
  useEffect(() => {
    authService.me()
      .then((sessao) => aplicarSessao(sessao))
      .catch(() => {
        // 401 (sem sessão) é o caminho normal: fica na tela de login mesmo.
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const el = googleWrapRef.current
    if (!el) return
    const medir = () => {
      const w = Math.round(el.getBoundingClientRect().width)
      setLarguraGoogle(Math.min(400, Math.max(200, w)))
    }
    // observe() já dispara o callback com o tamanho atual (assíncrono) — sem setState no corpo do efeito
    const ro = new ResizeObserver(medir)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  return (
    <div className={styles.page}>
      <div className={styles.card}>

        <div className={styles.header}>
          <Image
            src="/images/logo.png"
            alt="Domus"
            width={280}
            height={277}
            className={styles.logoImg}
          />
          <p className={styles.subtitulo}>Bem vindo de volta! Entre na sua conta</p>
        </div>

        <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
          <Input
            id="email"
            type="email"
            label="E-MAIL"
            placeholder="nome@igreja.com.br"
            autoComplete="email"
            leftIcon={<Mail size={16} />}
            error={errors.email?.message}
            {...register('email')}
          />

          <Input
            id="senha"
            type={mostrarSenha ? 'text' : 'password'}
            label="SENHA"
            placeholder="••••••••"
            autoComplete="current-password"
            leftIcon={<Lock size={16} />}
            rightElement={
              <button
                type="button"
                className={styles.toggleSenha}
                onClick={() => setMostrarSenha(prev => !prev)}
                aria-label={mostrarSenha ? 'Esconder senha' : 'Mostrar senha'}
              >
                {mostrarSenha ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            }
            error={errors.senha?.message}
            {...register('senha')}
          />

          <div className={styles.forgotRow}>
            <Link href="/forgot-password" className={styles.forgotLink}>
              Esqueci minha senha
            </Link>
          </div>

          {contaSemSenha ? (
            <div className={styles.avisoGoogle}>
              <p>Esta conta usa login com Google. Entre com Google abaixo ou defina uma senha para acessar por e-mail.</p>
              <Link
                href={`/forgot-password?email=${encodeURIComponent(emailDigitado)}`}
                className={styles.definirSenhaLink}
              >
                Definir senha
              </Link>
            </div>
          ) : (
            erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>
          )}

          <Button
            type="submit"
            variant="primary"
            size="lg"
            isLoading={isLoading}
            loadingText='Entrando...'
            disabled={isButtonDisabled}
            suppressHydrationWarning
            style={{ width: '100%' }}
          >
            Entrar
          </Button>

        </form>

        <div className={styles.divider}>
          <span className={styles.dividerText}>OU</span>
        </div>

        <div className={styles.googleWrap} ref={googleWrapRef}>
          <GoogleLogin
            onSuccess={(cred) => {
              if (cred.credential) onGoogleLogin(cred.credential)
            }}
            onError={onGoogleError}
            text="signin_with"
            width={String(larguraGoogle)}
          />
        </div>

        <div className={styles.footer}>
          <span className={styles.footerText}>Ainda não tem conta?</span>
          <Link href="/cadastro" className={styles.footerLink}>
            Cadastre sua igreja
          </Link>
        </div>
      </div>

      <p className={styles.copyright}>© 2026 DOMUS Gestão Eclesiástica</p>
    </div>
  )
}