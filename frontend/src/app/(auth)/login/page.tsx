'use client'

import Link from 'next/link'
import { Eye, EyeOff, Mail, Lock } from 'lucide-react'
import { useState } from 'react'
import { useLogin } from '@/hooks/useLogin'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './page.module.css'
import Image from 'next/image'

export default function LoginPage() {
  const [mostrarSenha, setMostrarSenha] = useState(false)
  const { register, handleSubmit, errors, erroGeral, isLoading, isValid, onSubmit } = useLogin()

  return (
    <div className={styles.page}>
      <div className={styles.card}>

        <div className={styles.header}>
          <img
              src="https://lh3.googleusercontent.com/aida/ADBb0ugF_LxPDfFGtOttlNuygD0fnCDraW0VvU9islvbAc3KgpucAjDgD5JO4SzNO3OpwJn7psp6ep4fHXZUUWq8_i7OJxjUDGKxjLaBTjtOgScF8ynQUdnoImjPGzQS6JlgVl83Y7dAKA7K63D3Mc7FIHHwJCe90Ws9t07akD_RH4KRzj32yCU2tdl3jj94MpldX5b0cirpPofxPy4fh3fBpYiRx9WMXCqmNpxMVuDhEV5uW1KrMQtmGKWnlZs=s1600?authuser=1"
              alt="Domus"
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
            disabled={!isValid || isLoading}
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