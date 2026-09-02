'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { clsx } from 'clsx'
import { User, MapPin, FileText, Info } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import { Button } from '@/components/common/button/Button'
import { Select } from '@/components/common/select/Select'
import { StatusCards } from '@/components/common/statuscards/StatusCards'
import { formatarTelefone, formatarCep } from '@/lib/masks'
import { UF_OPTIONS } from '@/lib/ufs'
import styles from './VisitanteForm.module.css'
import type { UseFormReturn } from 'react-hook-form'

const SEXO_OPTIONS = [
  { value: 'HOMEM', titulo: 'Homem' },
  { value: 'MULHER', titulo: 'Mulher' },
]

const ESTADO_CIVIL_OPTIONS = [
  { value: 'SOLTEIRO', label: 'Solteiro(a)' },
  { value: 'CASADO', label: 'Casado(a)' },
  { value: 'DIVORCIADO', label: 'Divorciado(a)' },
  { value: 'VIUVO', label: 'Viúvo(a)' },
]

export type VisitanteFormData = {
  nome: string
  telefone?: string
  dataNascimento?: string
  sexo?: string
  estadoCivil?: string
  endereco: {
    cep?: string
    logradouro?: string
    numero?: string
    complemento?: string
    bairro?: string
    cidade?: string
    uf?: string
  }
  temFilhos?: boolean
  quantidadeFilhos?: number | null
  observacoes?: string
}

type VisitanteFormProps = UseFormReturn<VisitanteFormData> & {
  isFormIncomplete: boolean
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  onSubmit: (data: VisitanteFormData) => void
  // Quando renderizado dentro de um modal: tira o "cartão" de cada seção e a coluna
  // grudada, ficando um fluxo único estilo app mobile.
  emModal?: boolean
  // Se passado, o "Cancelar" chama isto em vez de router.back() (usado no modal).
  onCancel?: () => void
}

export function VisitanteForm(props: VisitanteFormProps) {
  const router = useRouter()
  const {
    register, handleSubmit, setValue, watch,
    formState: { errors },
    erroGeral, isLoading, isFormIncomplete, onSubmit, ehEdicao, emModal, onCancel,
  } = props

  const sexoAtual = watch('sexo') ?? ''
  const temFilhosAtual = watch('temFilhos') ?? false
  const dataNascimentoAtual = (watch('dataNascimento') as string | undefined) ?? ''

  const { buscar, carregando: carregandoCep } = useBuscaCep()
  const [cepNaoEncontrado, setCepNaoEncontrado] = useState(false)
  const cepReg = register('endereco.cep')

  async function aoSairDoCep(e: React.FocusEvent<HTMLInputElement>) {
    setCepNaoEncontrado(false)
    const achado = await buscar(e.target.value)
    if (!achado) {
      if (e.target.value.replace(/\D/g, '').length === 8) setCepNaoEncontrado(true)
      return
    }
    if (achado.logradouro) setValue('endereco.logradouro', achado.logradouro)
    if (achado.bairro) setValue('endereco.bairro', achado.bairro)
    if (achado.cidade) setValue('endereco.cidade', achado.cidade)
    if (achado.uf) setValue('endereco.uf', achado.uf)
  }

  return (
    <form className={clsx(styles.form, emModal && styles.emModal)} onSubmit={handleSubmit(onSubmit)}>
      <div className={styles.colunas}>
        <div className={styles.colunaEsquerda}>
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
              <Input id="telefone" label="TELEFONE" placeholder="(00) 00000-0000" inputMode="numeric"
                error={errors.telefone?.message} {...register('telefone')}
                onChange={(e) => setValue('telefone', formatarTelefone(e.target.value), { shouldValidate: true })} />
              <CampoData id="dataNascimento" label="DATA DE NASCIMENTO"
                value={dataNascimentoAtual} erro={errors.dataNascimento?.message}
                onChange={(v) => setValue('dataNascimento', v, { shouldValidate: true })} />
              <Select id="estadoCivil" label="ESTADO CIVIL" placeholder="Selecione"
                options={ESTADO_CIVIL_OPTIONS} error={errors.estadoCivil?.message} {...register('estadoCivil')} />
            </div>
            <StatusCards label="SEXO" options={SEXO_OPTIONS}
              selecionado={sexoAtual} {...register('sexo')} />
          </section>

          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><MapPin size={20} /></span>
              <h2 className={styles.secaoTitulo}>Endereço</h2>
            </div>
            <div className={styles.grid2}>
              <div className={styles.spanFull}>
                <Input id="cep" label="CEP" placeholder="00000-000" inputMode="numeric" maxLength={9}
                  error={errors.endereco?.cep?.message}
                  {...cepReg}
                  onChange={(e) => setValue('endereco.cep', formatarCep(e.target.value), { shouldValidate: true })}
                  onBlur={(e) => { cepReg.onBlur(e); void aoSairDoCep(e) }} />
                {carregandoCep && <span className={styles.erroCampo}>buscando CEP…</span>}
                {cepNaoEncontrado && (
                  <span className={styles.erroCampo}>CEP não encontrado — preencha manualmente.</span>
                )}
              </div>
              <div className={styles.spanFull}>
                <Input id="logradouro" label="LOGRADOURO" placeholder="Rua, avenida…"
                  error={errors.endereco?.logradouro?.message} {...register('endereco.logradouro')} />
              </div>
              <Input id="numero" label="NÚMERO" placeholder="123, s/n…"
                error={errors.endereco?.numero?.message} {...register('endereco.numero')} />
              <Input id="complemento" label="COMPLEMENTO" placeholder="Apto, bloco…"
                error={errors.endereco?.complemento?.message} {...register('endereco.complemento')} />
              <Input id="bairro" label="BAIRRO"
                error={errors.endereco?.bairro?.message} {...register('endereco.bairro')} />
              <Input id="cidade" label="CIDADE"
                error={errors.endereco?.cidade?.message} {...register('endereco.cidade')} />
              <Select id="uf" label="UF" placeholder="UF"
                options={UF_OPTIONS} error={errors.endereco?.uf?.message} {...register('endereco.uf')} />
            </div>
          </section>

          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><FileText size={20} /></span>
              <h2 className={styles.secaoTitulo}>Observações</h2>
            </div>
            <div className={styles.campoTextarea}>
              <label className={styles.labelTextarea} htmlFor="observacoes">NOTAS ADICIONAIS</label>
              <textarea id="observacoes" className={styles.textarea}
                placeholder="Informações relevantes sobre o visitante..." {...register('observacoes')} />
            </div>
          </section>
        </div>

        <div className={styles.colunaDireita}>
          <section className={styles.secaoFilhos}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><Info size={20} /></span>
              <h2 className={styles.secaoTitulo}>Filhos</h2>
            </div>
            <div className={styles.filhosWrap}>
              <label className={styles.checkboxLabel}>
                <input type="checkbox"
                  checked={temFilhosAtual}
                  onChange={(e) => {
                    setValue('temFilhos', e.target.checked, { shouldValidate: true })
                    if (!e.target.checked) setValue('quantidadeFilhos', null)
                  }} />
                <span>Tem filhos?</span>
              </label>
              {temFilhosAtual && (
                <Input id="quantidadeFilhos" label="QUANTIDADE" type="number" inputMode="numeric" min="0"
                  error={errors.quantidadeFilhos?.message}
                  {...register('quantidadeFilhos', { valueAsNumber: true })}
                  onChange={(e) => {
                    const val = e.target.value === '' ? null : Number(e.target.value)
                    setValue('quantidadeFilhos', val, { shouldValidate: true })
                  }} />
              )}
            </div>
          </section>

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

          <div className={styles.acoes}>
            <Button type="submit" variant="primary" size="lg"
              isLoading={isLoading} disabled={isFormIncomplete || isLoading} style={{ width: '100%' }}>
              {ehEdicao ? 'Salvar alterações' : 'Salvar visitante'}
            </Button>
            <button type="button" onClick={() => (onCancel ? onCancel() : router.back())} className={styles.cancelarLink}>Cancelar</button>
          </div>
        </div>
      </div>
    </form>
  )
}
