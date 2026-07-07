'use client'

import Link from 'next/link'
import { Eye, EyeOff, Mail, Lock } from 'lucide-react'
import { useState } from 'react'
import { useLogin } from '@/hooks/auth/useLogin'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './page.module.css'
import Image from 'next/image'

export default function LoginPage() {
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const { register, handleSubmit, errors, erroGeral, isLoading, isButtonDisabled, onSubmit } = useLogin()

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
            labelRight={
              <Link href="/esqueci-senha" className={styles.forgotLink}>
                Esqueci minha senha
              </Link>
            }
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

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

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

        <div className={styles.footer}>
          <span className={styles.footerText}>Ainda não tem conta?</span>
          <Link href="/cadastro" className={styles.footerLink}>
            Cadastre sua igreja
          </Link>
        </div>

      </div>

      <p className={styles.copyright}>© 2024 DOMUS Management System</p>
    </div>
  )
}