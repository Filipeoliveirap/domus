'use client'

import Link from 'next/link'
import { User, MapPin, FileText, Church, Info } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { Select } from '@/components/common/select/Select'
import { StatusCards } from '@/components/common/statuscards/StatusCards'
import { MinisterioInput } from '@/components/module/membros/MinisterioInput'
import { formatarTelefone } from '@/lib/masks'
import styles from './MembroForm.module.css'
import type { UseFormReturn } from 'react-hook-form'
import type { MembroFormInput, MembroFormData } from '@/lib/validators'

const STATUS_OPTIONS = [
  { value: 'ATIVO', titulo: 'Ativo', descricao: 'Membro regular com participação frequente.' },
  { value: 'INATIVO', titulo: 'Inativo', descricao: 'Membro afastado ou que solicitou saída.' },
  { value: 'VISITANTE', titulo: 'Visitante', descricao: 'Pessoa em processo de integração.' },
]

const ESTADO_CIVIL_OPTIONS = [
  { value: 'SOLTEIRO', label: 'Solteiro(a)' },
  { value: 'CASADO', label: 'Casado(a)' },
  { value: 'DIVORCIADO', label: 'Divorciado(a)' },
  { value: 'VIUVO', label: 'Viúvo(a)' },
]

type MembroFormProps = UseFormReturn<MembroFormInput, unknown, MembroFormData> & {
  isFormIncomplete: boolean
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  onSubmit: (data: MembroFormData) => void
}

export function MembroForm(props: MembroFormProps) {
  const {
    register, handleSubmit, setValue, watch,
    formState: { errors },
    erroGeral, isLoading, isFormIncomplete, onSubmit, ehEdicao,
  } = props

  const statusAtual = watch('status')
  const ministerioAtual = (watch('ministerio') as string | undefined) ?? ''

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
      <div className={styles.colunas}>
        <div className={styles.colunaEsquerda}>
          {/* Informações pessoais */}
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><User size={20} /></span>
              <h2 className={styles.secaoTitulo}>Informações pessoais</h2>
            </div>
            <div className={styles.grid2}>
              <div className={styles.spanFull}>
                <Input id="nome" label="NOME COMPLETO*" placeholder="Ex: João da Silva"
                  error={errors.nome?.message} {...register('nome')} />
              </div>
              <Input id="email" type="email" label="E-MAIL" placeholder="exemplo@dominio.com"
                error={errors.email?.message} {...register('email')} />
              <Input id="telefone" label="TELEFONE" placeholder="(00) 00000-0000" inputMode="numeric"
                error={errors.telefone?.message} {...register('telefone')}
                onChange={(e) => setValue('telefone', formatarTelefone(e.target.value), { shouldValidate: true })} />
              <Input id="dataNascimento" type="date" label="DATA DE NASCIMENTO"
                error={errors.dataNascimento?.message} {...register('dataNascimento')} />
              <Select id="estadoCivil" label="ESTADO CIVIL" placeholder="Selecione"
                options={ESTADO_CIVIL_OPTIONS} error={errors.estadoCivil?.message} {...register('estadoCivil')} />
            </div>
          </section>

          {/* Localização */}
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><MapPin size={20} /></span>
              <h2 className={styles.secaoTitulo}>Localização</h2>
            </div>
            <div className={styles.campoTextarea}>
              <label className={styles.labelTextarea} htmlFor="endereco">ENDEREÇO</label>
              <textarea id="endereco" className={styles.textarea}
                placeholder="Rua, Número, Bairro, Cidade, Estado, CEP..." {...register('endereco')} />
              {errors.endereco && <span className={styles.erroCampo}>{errors.endereco.message}</span>}
            </div>
          </section>

          {/* Observações */}
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><FileText size={20} /></span>
              <h2 className={styles.secaoTitulo}>Observações</h2>
            </div>
            <div className={styles.campoTextarea}>
              <label className={styles.labelTextarea} htmlFor="observacoes">NOTAS ADICIONAIS</label>
              <textarea id="observacoes" className={styles.textarea}
                placeholder="Informações relevantes sobre o membro..." {...register('observacoes')} />
            </div>
          </section>
        </div>

        <div className={styles.colunaDireita}>
          <section className={styles.secaoIgreja}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><Church size={20} /></span>
              <h2 className={styles.secaoTitulo}>Dados da igreja</h2>
            </div>

            <StatusCards label="STATUS DO MEMBRO" options={STATUS_OPTIONS}
              selecionado={statusAtual} {...register('status')} />

            <div className={styles.ministerioWrap}>
              <span className={styles.labelMinisterio}>MINISTÉRIO</span>
              <MinisterioInput id="ministerio" value={ministerioAtual}
                error={errors.ministerio?.message} registerProps={register('ministerio')}
                onSelecionarSugestao={(valor) => setValue('ministerio', valor, { shouldValidate: true })} />
            </div>

            <div className={styles.infoBox}>
              <Info size={18} className={styles.infoIcon} />
              <p className={styles.infoText}>
                Os dados da igreja ajudam na organização interna e alocação ministerial.
              </p>
            </div>
          </section>

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

          <div className={styles.acoes}>
            <Button type="submit" variant="primary" size="lg"
              isLoading={isLoading} disabled={isFormIncomplete || isLoading} style={{ width: '100%' }}>
              {ehEdicao ? 'Salvar alterações' : 'Salvar membro'}
            </Button>
            <Link href="/membros" className={styles.cancelarLink}>Cancelar</Link>
          </div>
        </div>
      </div>
    </form>
  )
}