'use client'

import { useState } from 'react'
import Link from 'next/link'
import { ArrowLeft, Eye, EyeOff, User, Mail, Lock, RotateCcw } from 'lucide-react'
import type { UseFormRegister, UseFormHandleSubmit, FieldErrors, UseFormWatch } from 'react-hook-form'
import type { RegistrarIgrejaFormData2 } from '@/lib/validators'
import { Input } from '../../../components/common/input/Input'
import { Button } from '../../../components/common/button/Button'
import { PasswordStrengthIndicator } from './PasswordStrengthIndicator'
import styles from './Passo2.module.css'

interface Passo2Props {
  register: UseFormRegister<RegistrarIgrejaFormData2>
  handleSubmit: UseFormHandleSubmit<RegistrarIgrejaFormData2>
  errors: FieldErrors<RegistrarIgrejaFormData2>
  isValid: boolean
  watch: UseFormWatch<RegistrarIgrejaFormData2>
  erroGeral: string | null
  isLoading: boolean
  onSubmit: (data: RegistrarIgrejaFormData2) => Promise<void>
  onVoltar: () => void
}

export function Passo2({
  register, handleSubmit, errors, isValid, watch,
  erroGeral, isLoading, onSubmit, onVoltar,
}: Passo2Props) {

  const [mostrarSenha, setMostrarSenha] = useState(false)
  const [mostrarConfirmar, setMostrarConfirmar] = useState(false)

  // watch acompanha o valor da senha em tempo real para o indicador de força
  const senha = watch('senhaAdmin') || ''

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>

      {/* Header — título + subtítulo */}
      <header className={styles.header}>
        <h2 className={styles.title}>Crie sua conta de administrador</h2>
        <p className={styles.subtitle}>
          Preencha seus dados pessoais para gerenciar o Domus.
        </p>
      </header>

      {/* Nome completo */}
      <Input
        id="nomeAdmin"
        label="NOME COMPLETO*"
        placeholder="Ex: João Silva"
        autoComplete="name"
        leftIcon={<User size={16} />}
        error={errors.nomeAdmin?.message}
        {...register('nomeAdmin')}
      />

      {/* E-mail */}
      <Input
        id="emailAdmin"
        type="email"
        label="E-MAIL*"
        placeholder="nome@igreja.org"
        autoComplete="email"
        leftIcon={<Mail size={16} />}
        error={errors.emailAdmin?.message}
        {...register('emailAdmin')}
      />

      {/* Senha + indicador de força */}
      <div className={styles.senhaGroup}>
        <Input
          id="senhaAdmin"
          type={mostrarSenha ? 'text' : 'password'}
          label="SENHA*"
          placeholder="••••••••"
          autoComplete="new-password"
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
          error={errors.senhaAdmin?.message}
          {...register('senhaAdmin')}
        />

        <PasswordStrengthIndicator senha={senha} />
      </div>

      {/* Confirmar senha */}
      <Input
        id="confirmarSenha"
        type={mostrarConfirmar ? 'text' : 'password'}
        label="CONFIRMAR SENHA*"
        placeholder="••••••••"
        autoComplete="new-password"
        leftIcon={<RotateCcw size={16} />}
        rightElement={
          <button
            type="button"
            className={styles.toggleSenha}
            onClick={() => setMostrarConfirmar(prev => !prev)}
            aria-label={mostrarConfirmar ? 'Esconder senha' : 'Mostrar senha'}
          >
            {mostrarConfirmar ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        }
        error={errors.confirmarSenha?.message}
        {...register('confirmarSenha')}
      />

      {/* Checkbox de termos */}
      <div className={styles.termosWrapper}>
        <label className={styles.termosLabel}>
          <input
            type="checkbox"
            className={styles.checkbox}
            {...register('aceitouTermos')}
          />
          <span className={styles.termosTexto}>
            Ao criar minha conta, eu concordo com os{' '}
            <Link href="/termos" className={styles.termosLink}>Termos de Uso</Link>
            {' '}e a{' '}
            <Link href="/privacidade" className={styles.termosLink}>Política de Privacidade</Link>
            {' '}do Domus.
          </span>
        </label>
        {errors.aceitouTermos && (
          <span className={styles.termosErro}>{errors.aceitouTermos.message}</span>
        )}
      </div>

      {/* Erro geral da API */}
      {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

      {/* Botão Criar conta — full width */}
      <Button
        type="submit"
        variant="primary"
        size="lg"
        isLoading={isLoading}
        disabled={!isValid || isLoading}
        style={{ width: '100%' }}
      >
        Criar conta
      </Button>

      {/* Link Voltar — discreto, abaixo do botão */}
      <button
        type="button"
        className={styles.voltarLink}
        onClick={onVoltar}
        disabled={isLoading}
      >
        <ArrowLeft size={14} />
        <span>Voltar</span>
      </button>

    </form>
  )
}