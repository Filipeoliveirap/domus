'use client'

import { Suspense } from 'react'
import Link from 'next/link'
import { Mail, KeyRound, MailCheck, ArrowLeft } from 'lucide-react'
import { useEsqueciSenha } from '@/hooks/auth/useEsqueciSenha'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './page.module.css'

function EsqueciSenhaConteudo() {
  const { register, handleSubmit, errors, erroGeral, isLoading, isButtonDisabled, onSubmit, enviado, emailEnviado } =
    useEsqueciSenha()

  return (
      <div className={styles.card}>
        {enviado ? (
          <>
            <div className={styles.header}>
              <span className={styles.iconBadge}>
                <MailCheck size={28} />
              </span>
              <h1 className={styles.titulo}>E-mail enviado</h1>
              <p className={styles.subtitulo}>
                Se houver uma conta associada a{' '}
                <span className={styles.emailDestaque}>{emailEnviado}</span>, enviamos um link para redefinir
                sua senha. Verifique sua caixa de entrada e o spam.
              </p>
            </div>

            <div className={styles.footer}>
              <Link href="/login" className={styles.backLink}>
                <ArrowLeft size={18} />
                Voltar para o login
              </Link>
            </div>
          </>
        ) : (
          <>
            <div className={styles.header}>
              <span className={styles.iconBadge}>
                <KeyRound size={28} />
              </span>
              <h1 className={styles.titulo}>Esqueci minha senha</h1>
              <p className={styles.subtitulo}>
                Insira seu e-mail abaixo para receber as instruções de recuperação.
              </p>
            </div>

            <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
              <Input
                id="email"
                type="email"
                label="E-MAIL CADASTRADO"
                placeholder="nome@igreja.com.br"
                autoComplete="email"
                leftIcon={<Mail size={16} />}
                error={errors.email?.message}
                {...register('email')}
              />

              {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

              <Button
                type="submit"
                variant="primary"
                size="lg"
                isLoading={isLoading}
                loadingText="Enviando..."
                disabled={isButtonDisabled}
                suppressHydrationWarning
                style={{ width: '100%' }}
              >
                Enviar link de recuperação
              </Button>
            </form>

            <div className={styles.footer}>
              <Link href="/login" className={styles.backLink}>
                <ArrowLeft size={18} />
                Voltar para o login
              </Link>
            </div>
          </>
        )}
      </div>
  )
}

export default function EsqueciSenhaPage() {
  return (
    <div className={styles.page}>
      <Suspense fallback={null}>
        <EsqueciSenhaConteudo />
      </Suspense>
      <p className={styles.copyright}>© 2026 DOMUS Gestão Eclesiástica</p>
    </div>
  )
}
