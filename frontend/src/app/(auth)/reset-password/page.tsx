'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { GoogleLogin } from '@react-oauth/google'
import { Lock, LockKeyhole, KeyRound, Eye, EyeOff, ArrowLeft, CheckCircle2, TimerOff } from 'lucide-react'
import { useRedefinirSenha } from '@/hooks/auth/useRedefinirSenha'
import { useLogin } from '@/hooks/auth/useLogin'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { PasswordStrengthIndicator } from '../cadastro/PasswordStrengthIndicator'
import styles from './page.module.css'

function RedefinirSenhaConteudo() {
  const router = useRouter()
  const searchParams = useSearchParams()
  // Modo convite (link vindo do e-mail de convite): mostra também o "Entrar com Google".
  const convite = searchParams.get('convite') === '1'
  const { onGoogleLogin, onGoogleError } = useLogin()
  const { register, handleSubmit, watch, errors, erroGeral, isLoading, isButtonDisabled, onSubmit, sucesso, linkInvalido } =
    useRedefinirSenha()
  const [mostrarNova, setMostrarNova] = useState(false)
  const [mostrarConfirmar, setMostrarConfirmar] = useState(false)
  const senha = watch('novaSenha') ?? ''

  if (linkInvalido) {
    return (
      <div className={styles.card}>
        <div className={styles.header}>
          <span className={styles.iconBadgeError}>
            <TimerOff size={34} />
          </span>
          <h1 className={styles.titulo}>Link expirado ou inválido</h1>
          <p className={styles.subtitulo}>
            Por segurança, o link de redefinição expira em 30 minutos ou depois de ser usado uma vez.
            Solicite um novo para continuar.
          </p>
        </div>

        <div className={styles.acoes}>
          <Button
            type="button"
            variant="primary"
            size="lg"
            onClick={() => router.push('/forgot-password')}
            style={{ width: '100%' }}
          >
            Solicitar novo link
          </Button>
        </div>

        <div className={styles.footer}>
          <Link href="/login" className={styles.backLink}>
            <ArrowLeft size={18} />
            Voltar para o login
          </Link>
        </div>
      </div>
    )
  }

  if (sucesso) {
    return (
      <div className={styles.card}>
        <div className={styles.header}>
          <span className={styles.iconBadgeSuccess}>
            <CheckCircle2 size={38} />
          </span>
          <h1 className={styles.titulo}>Senha redefinida com sucesso!</h1>
          <p className={styles.subtitulo}>
            Sua conta está protegida. Estamos redirecionando você para o login em alguns segundos...
          </p>
        </div>

        <div className={styles.footer}>
          <Link href="/login" className={styles.backLink}>
            <ArrowLeft size={18} />
            Ir para o login agora
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <span className={styles.iconBadge}>
          <KeyRound size={28} />
        </span>
        <h1 className={styles.titulo}>{convite ? 'Defina sua senha de acesso' : 'Redefinir sua senha'}</h1>
        <p className={styles.subtitulo}>
          {convite
            ? 'Você foi convidado para o Domus. Crie uma senha para entrar — ou use sua conta Google.'
            : 'Crie uma nova senha segura para acessar sua conta.'}
        </p>
      </div>

      <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
        <div>
          <Input
            id="novaSenha"
            type={mostrarNova ? 'text' : 'password'}
            label="NOVA SENHA"
            placeholder="••••••••"
            autoComplete="new-password"
            leftIcon={<Lock size={16} />}
            rightElement={
              <button
                type="button"
                className={styles.toggleSenha}
                onClick={() => setMostrarNova((prev) => !prev)}
                aria-label={mostrarNova ? 'Esconder senha' : 'Mostrar senha'}
              >
                {mostrarNova ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            }
            error={errors.novaSenha?.message}
            {...register('novaSenha')}
          />
          <PasswordStrengthIndicator senha={senha} />
        </div>

        <Input
          id="confirmarSenha"
          type={mostrarConfirmar ? 'text' : 'password'}
          label="CONFIRMAR NOVA SENHA"
          placeholder="••••••••"
          autoComplete="new-password"
          leftIcon={<LockKeyhole size={16} />}
          rightElement={
            <button
              type="button"
              className={styles.toggleSenha}
              onClick={() => setMostrarConfirmar((prev) => !prev)}
              aria-label={mostrarConfirmar ? 'Esconder senha' : 'Mostrar senha'}
            >
              {mostrarConfirmar ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          }
          error={errors.confirmarSenha?.message}
          {...register('confirmarSenha')}
        />

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

        <Button
          type="submit"
          variant="primary"
          size="lg"
          isLoading={isLoading}
          loadingText="Redefinindo..."
          disabled={isButtonDisabled}
          suppressHydrationWarning
          style={{ width: '100%' }}
        >
          {convite ? 'Definir senha' : 'Redefinir senha'}
        </Button>
      </form>

      {convite && (
        <>
          <div className={styles.divider}><span className={styles.dividerText}>OU</span></div>
          <div className={styles.googleWrap}>
            <GoogleLogin
              onSuccess={(cred) => { if (cred.credential) onGoogleLogin(cred.credential) }}
              onError={onGoogleError}
              text="continue_with"
              width="280"
            />
          </div>
        </>
      )}

      <div className={styles.footer}>
        <Link href="/login" className={styles.backLink}>
          <ArrowLeft size={18} />
          Voltar para o login
        </Link>
      </div>
    </div>
  )
}

export default function RedefinirSenhaPage() {
  return (
    <div className={styles.page}>
      <Suspense fallback={null}>
        <RedefinirSenhaConteudo />
      </Suspense>
      <p className={styles.copyright}>© 2026 DOMUS Gestão Eclesiástica</p>
    </div>
  )
}
