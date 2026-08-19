'use client'

import Link from 'next/link'
import { ArrowLeft, ArrowRight, Mail } from 'lucide-react'
import { GoogleLogin } from '@react-oauth/google'
import type { UseFormRegister, UseFormHandleSubmit, FieldErrors, UseFormSetValue } from 'react-hook-form'
import type { RegistrarIgrejaFormData1 } from '@/lib/validators'
import { Input } from '../../../components/common/input/Input'
import { Button } from '../../../components/common/button/Button'
import styles from './Passo1.module.css'
import { formatarCnpj, formatarTelefone } from '@/lib/masks'

interface Passo1Props {
  register: UseFormRegister<RegistrarIgrejaFormData1>
  handleSubmit: UseFormHandleSubmit<RegistrarIgrejaFormData1>
  errors: FieldErrors<RegistrarIgrejaFormData1>
  passo1Incompleto: boolean
  setValue: UseFormSetValue<RegistrarIgrejaFormData1>
  onAvancar: (data: RegistrarIgrejaFormData1) => void
  googleData: { nome: string; email: string } | null
  onGoogleAuth: (idToken: string) => void
  onGoogleError: () => void
  onSubmitGoogle: (data: RegistrarIgrejaFormData1) => void
  erroGeral: string | null
  isLoading: boolean
  aceitouTermosGoogle: boolean
  setAceitouTermosGoogle: (v: boolean) => void
}

export function Passo1({
  register, handleSubmit, setValue, errors, passo1Incompleto, onAvancar,
  googleData, onGoogleAuth, onGoogleError, onSubmitGoogle, erroGeral, isLoading,
  aceitouTermosGoogle, setAceitouTermosGoogle,
}: Passo1Props) {
  const modoGoogle = googleData !== null

  return (
    <div className={styles.container}>

      <header className={styles.header}>
        <h2 className={styles.title}>Cadastre sua igreja</h2>
        <p className={styles.subtitle}>
          Inicie a transformação digital da sua comunidade hoje mesmo.
        </p>
      </header>

      {modoGoogle ? (
        <div className={styles.googleBanner}>
          Cadastrando como <strong>{googleData!.nome}</strong> ({googleData!.email})
        </div>
      ) : (
        <>
          <div className={styles.googleWrap}>
            <GoogleLogin
              onSuccess={(cred) => {
                if (cred.credential) onGoogleAuth(cred.credential)
              }}
              onError={onGoogleError}
              text="signup_with"
              width="280"
            />
          </div>
          <div className={styles.divider}>
            <span className={styles.dividerText}>OU PREENCHA MANUALMENTE</span>
          </div>
        </>
      )}

      {/* Formulário */}
      <form className={styles.form} onSubmit={handleSubmit(modoGoogle ? onSubmitGoogle : onAvancar)}>

        {/* Campo 1: Nome da igreja — full width */}
        <Input
          id="nomeIgreja"
          label="NOME DA IGREJA*"
          placeholder="Ex: Comunidade Batista do Calvário"
          autoComplete="organization"
          error={errors.nomeIgreja?.message}
          {...register('nomeIgreja')}
        />

        {/* Campos 2 e 3: CNPJ + Telefone lado a lado em grid */}
        <div className={styles.row}>
          <Input
            id="cnpj"
            label="CNPJ (OPCIONAL)"
            placeholder="00.000.000/0000-00"
            inputMode="numeric"
            error={errors.cnpj?.message}
            {...register('cnpj')}
            onChange={(e) => {
              const formatado = formatarCnpj(e.target.value)
              e.target.value = formatado
              setValue('cnpj', formatado, {
                shouldValidate: true,
                shouldDirty: true,
              })
            }}
          />
          <Input
            id="telefoneContato"
            label="TELEFONE DE CONTATO"
            placeholder="(00) 00000-0000"
            autoComplete="tel"
            inputMode="numeric"
            error={errors.telefoneContato?.message}
            {...register('telefoneContato')}
            onChange={(e) => {
              const formatado = formatarTelefone(e.target.value)
              e.target.value = formatado
              setValue('telefoneContato', formatado, {
                shouldValidate: true,
                shouldDirty: true,
              })
            }}
          />
        </div>

        {/* Campo 4: E-mail de contato — full width com ícone */}
        <Input
          id="emailContato"
          type="email"
          label="E-MAIL DE CONTATO*"
          placeholder="contato@igreja.org.br"
          autoComplete="email"
          leftIcon={<Mail size={16} />}
          error={errors.emailContato?.message}
          readOnly={modoGoogle}
          {...register('emailContato')}
        />

        {modoGoogle && (
          <div className={styles.termosWrapper}>
            <label className={styles.termosLabel}>
              <input
                type="checkbox"
                className={styles.checkbox}
                checked={aceitouTermosGoogle}
                onChange={(e) => setAceitouTermosGoogle(e.target.checked)}
              />
              <span className={styles.termosTexto}>
                Li e concordo com os{' '}
                <Link href="/termos" className={styles.termosLink} target="_blank">Termos de Uso</Link>
                {' '}e a{' '}
                <Link href="/privacidade" className={styles.termosLink} target="_blank">Política de Privacidade</Link>
                {' '}do Domus.
              </span>
            </label>
          </div>
        )}

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

        {/* Action bar — Voltar (para login) + Próximo/Concluir */}
        <div className={styles.actions}>
          <Link href="/login" className={styles.voltarLink}>
            <ArrowLeft size={12} />
            <span>Voltar</span>
          </Link>

          <Button
            type="submit"
            variant="primary"
            size="md"
            disabled={passo1Incompleto || isLoading || (modoGoogle && !aceitouTermosGoogle)}
            isLoading={modoGoogle && isLoading}
            loadingText="Cadastrando..."
          >
            {modoGoogle ? 'Concluir cadastro' : 'Próximo'}
            {!modoGoogle && <ArrowRight size={12} />}
          </Button>
        </div>

      </form>
    </div>
  )
}