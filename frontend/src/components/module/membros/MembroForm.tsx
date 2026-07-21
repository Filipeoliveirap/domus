'use client'

import { useState } from 'react'
import Link from 'next/link'
import { User, MapPin, FileText, Church, Info } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { useBuscaCep } from '@/hooks/membro/useBuscaCep'
import { useBairros } from '@/hooks/membro/useBairros'
import { Button } from '@/components/common/button/Button'
import { Select } from '@/components/common/select/Select'
import { StatusCards } from '@/components/common/statuscards/StatusCards'
import { MinisterioInput } from '@/components/module/membros/MinisterioInput'
import { formatarTelefone, formatarCep } from '@/lib/masks'
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

const UF_OPTIONS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
].map((uf) => ({ value: uf, label: uf }))

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
  const batizadoAtual = watch('batizado')
  const dataNascimentoAtual = (watch('dataNascimento') as string | undefined) ?? ''
  const dataBatismoAtual = (watch('dataBatismo') as string | undefined) ?? ''

  // Auto-preenchimento por CEP (ViaCEP). Nunca trava: erro/CEP inexistente só sinalizam.
  const { buscar, carregando: carregandoCep } = useBuscaCep()
  const [cepNaoEncontrado, setCepNaoEncontrado] = useState(false)
  const cepReg = register('endereco.cep')

  // Bairros já existentes na igreja, para sugerir no <datalist> (padroniza a grafia).
  const { data: bairros } = useBairros()

  async function aoSairDoCep(e: React.FocusEvent<HTMLInputElement>) {
    setCepNaoEncontrado(false)
    const achado = await buscar(e.target.value)
    if (!achado) {
      if (e.target.value.replace(/\D/g, '').length === 8) setCepNaoEncontrado(true)
      return
    }
    // preenche o que a ViaCEP sabe; numero/complemento a pessoa completa
    if (achado.logradouro) setValue('endereco.logradouro', achado.logradouro)
    if (achado.bairro) setValue('endereco.bairro', achado.bairro)
    if (achado.cidade) setValue('endereco.cidade', achado.cidade)
    if (achado.uf) setValue('endereco.uf', achado.uf)
  }

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
              <CampoData id="dataNascimento" label="DATA DE NASCIMENTO"
                value={dataNascimentoAtual} erro={errors.dataNascimento?.message}
                onChange={(v) => setValue('dataNascimento', v, { shouldValidate: true })} />
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
              <Input id="bairro" label="BAIRRO" list="lista-bairros"
                error={errors.endereco?.bairro?.message} {...register('endereco.bairro')} />
              <datalist id="lista-bairros">
                {bairros?.map((b) => <option key={b} value={b} />)}
              </datalist>
              <Input id="cidade" label="CIDADE"
                error={errors.endereco?.cidade?.message} {...register('endereco.cidade')} />
              <Select id="uf" label="UF" placeholder="UF"
                options={UF_OPTIONS} error={errors.endereco?.uf?.message} {...register('endereco.uf')} />
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

            <div className={styles.batismoWrap}>
              <label className={styles.checkboxLabel}>
                <input type="checkbox" className={styles.checkbox} {...register('batizado')} />
                Batizado
              </label>
              {batizadoAtual && (
                <div>
                  <CampoData id="dataBatismo" label="DATA DE BATISMO"
                    value={dataBatismoAtual} erro={errors.dataBatismo?.message}
                    onChange={(v) => setValue('dataBatismo', v, { shouldValidate: true })} />
                  <span className={styles.campoHint}>Opcional</span>
                </div>
              )}
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