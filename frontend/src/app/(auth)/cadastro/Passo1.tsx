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

      <form className={styles.form} onSubmit={handleSubmit(modoGoogle ? onSubmitGoogle : onAvancar)}>
        <Input
          id="nomeIgreja"
          label="NOME DA IGREJA*"
          placeholder="Ex: Comunidade Batista do Calvário"
          autoComplete="organization"
          error={errors.nomeIgreja?.message}
          {...register('nomeIgreja')}
        />

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
              setValue('cnpj', formatado, { shouldValidate: true, shouldDirty: true })
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
              setValue('telefoneContato', formatado, { shouldValidate: true, shouldDirty: true })
            }}
          />
        </div>

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

        <div className={styles.acoes}>
          <Link href="/login" className={styles.voltar}>
            <ArrowLeft size={14} />
            <span>Voltar ao login</span>
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
            {!modoGoogle && <ArrowRight size={14} />}
          </Button>
        </div>
      </form>
    </div>
  )
}
