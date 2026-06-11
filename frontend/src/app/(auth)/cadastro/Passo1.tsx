'use client'

import Link from 'next/link'
import { ArrowLeft, ArrowRight, Mail } from 'lucide-react'
import type { UseFormRegister, UseFormHandleSubmit, FieldErrors } from 'react-hook-form'
import type { RegistrarIgrejaFormData1 } from '@/lib/validators'
import { Input } from '../../../components/common/input/Input'
import { Button } from '../../../components/common/button/Button'
import styles from './Passo1.module.css'

interface Passo1Props {
  register: UseFormRegister<RegistrarIgrejaFormData1>
  handleSubmit: UseFormHandleSubmit<RegistrarIgrejaFormData1>
  errors: FieldErrors<RegistrarIgrejaFormData1>
  isValid: boolean
  onAvancar: (data: RegistrarIgrejaFormData1) => void
}

export function Passo1({ register, handleSubmit, errors, isValid, onAvancar }: Passo1Props) {
  return (
    <div className={styles.container}>

      {/* Cabeçalho — título + subtítulo */}
      <header className={styles.header}>
        <h2 className={styles.title}>Cadastre sua igreja</h2>
        <p className={styles.subtitle}>
          Inicie a transformação digital da sua comunidade hoje mesmo.
        </p>
      </header>

      {/* Formulário */}
      <form className={styles.form} onSubmit={handleSubmit(onAvancar)}>

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
            error={errors.cnpj?.message}
            {...register('cnpj')}
          />
          <Input
            id="telefoneContato"
            label="TELEFONE DE CONTATO"
            placeholder="(00) 00000-0000"
            autoComplete="tel"
            error={errors.telefoneContato?.message}
            {...register('telefoneContato')}
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
          {...register('emailContato')}
        />

        {/* Action bar — Voltar (para login) + Próximo */}
        <div className={styles.actions}>
          <Link href="/login" className={styles.voltarLink}>
            <ArrowLeft size={12} />
            <span>Voltar</span>
          </Link>

          <Button
            type="submit"
            variant="primary"
            size="md"
            disabled={!isValid}
          >
            Próximo
            <ArrowRight size={12} />
          </Button>
        </div>

      </form>
    </div>
  )
}