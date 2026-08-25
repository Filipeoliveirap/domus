'use client'

import { useState } from 'react'
import { User, MapPin, FileText, Church } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useMinhaPessoa, useAtualizarMinhaFoto } from '@/hooks/pessoa/useMinhaPessoa'
import { notificar } from '@/components/common/Notificacao/notificar'
import { usePerfilForm } from '@/hooks/pessoa/usePerfilForm'
import { podeGerenciarPessoas } from '@/lib/permissoes'
import { AlterarSenhaForm } from '@/components/module/perfil/AlterarSenhaForm'
import { SkeletonPerfil } from './SkeletonPerfil'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { Input } from '@/components/common/input/Input'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { Select } from '@/components/common/select/Select'
import { StatusCards } from '@/components/common/statuscards/StatusCards'
import { Button } from '@/components/common/button/Button'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import { useBairros } from '@/hooks/pessoa/useBairros'
import { formatarTelefone, formatarCep } from '@/lib/masks'
import styles from './page.module.css'

const VINCULO_OPTIONS = [
  { value: 'MEMBRO', titulo: 'Membro', descricao: 'Batizado, formalmente membro da igreja.' },
  { value: 'CONGREGANTE', titulo: 'Congregante', descricao: 'Frequenta a igreja, não é batizado.' },
]

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

const UF_OPTIONS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG',
  'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
].map((uf) => ({ value: uf, label: uf }))

export default function PerfilPage() {
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const termosAceitosEm = useAuthStore((s) => s.termosAceitosEm)
  const podeEditarTudo = podeGerenciarPessoas(role, capacidadesExtras)

  const { data: pessoa, isLoading: carregando } = useMinhaPessoa()
  const atualizarFoto = useAtualizarMinhaFoto()
  const {
    register, handleSubmit, setValue, watch,
    formState: { errors },
    erroGeral, isLoading, onSubmit,
  } = usePerfilForm(pessoa)

  const vinculoAtual = watch('vinculo')
  const sexoAtual = watch('sexo')
  const dataNascimentoAtual = (watch('dataNascimento') as string | undefined) ?? ''
  const dataBatismoAtual = (watch('dataBatismo') as string | undefined) ?? ''
  const nomeAtual = (watch('nome') as string | undefined) ?? ''
  const fotoIdAtual = watch('fotoId') as string | null | undefined

  // Auto-preenchimento por CEP (ViaCEP). Só faz sentido para quem edita o endereço.
  const { buscar, carregando: carregandoCep } = useBuscaCep()
  const [cepNaoEncontrado, setCepNaoEncontrado] = useState(false)
  const cepReg = register('endereco.cep')
  const { data: bairros } = useBairros()

  async function aoSairDoCep(e: React.FocusEvent<HTMLInputElement>) {
    if (!podeEditarTudo) return
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

  if (carregando) return <SkeletonPerfil />

  return (
    <div className={styles.pagina}>
      <div className={styles.cabecalho}>
        <h1 className={styles.titulo}>Meu perfil</h1>
        <p className={styles.subtitulo}>Gerencie suas informações pessoais e segurança da conta.</p>
      </div>

      <form className={styles.card} onSubmit={handleSubmit(onSubmit)}>
        <div className={styles.fotoWrap}>
          <UploadFoto
            valor={fotoIdAtual}
            onChange={(id) => {
              const fotoAnterior = fotoIdAtual ?? null
              setValue('fotoId', id)
              atualizarFoto.mutate(id, {
                onSuccess: () => notificar.sucesso(id ? 'Foto atualizada.' : 'Foto removida.'),
                onError: (erro: unknown) => {
                  setValue('fotoId', fotoAnterior)
                  const mensagem =
                    (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                    'Tente novamente em alguns instantes.'
                  notificar.erro('Não foi possível salvar a foto', mensagem)
                },
              })
            }}
            formato="circulo"
            nomeFallback={nomeAtual}
          />
        </div>

        {!podeEditarTudo && (
          <div className={styles.aviso}>
            Seus dados só podem ser alterados pela secretaria da igreja, caso estejam
            incorretos ou desatualizados. Você pode trocar sua foto e sua senha a qualquer
            momento.
          </div>
        )}

        {/* ─── Informações pessoais ─────────────────────────── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><User size={20} /></span>
            <h2 className={styles.secaoTitulo}>Informações pessoais</h2>
          </div>
          <div className={styles.grid2}>
            <div className={styles.spanFull}>
              <Input id="nome" label="NOME COMPLETO*" placeholder="Ex: João da Silva"
                error={errors.nome?.message} disabled={!podeEditarTudo} {...register('nome')} />
            </div>
            {/* Email é a chave de login — sempre somente-leitura, inclusive para admin. */}
            <Input id="email" type="email" label="E-MAIL" disabled value={pessoa?.email ?? ''} />
            {/* Role vem da sessão, não é campo de Pessoa — sempre somente-leitura. */}
            <Input id="role" label="PERFIL / CARGO" disabled value={role ?? ''} />
            <Input id="telefone" label="TELEFONE" placeholder="(00) 00000-0000" inputMode="numeric"
              error={errors.telefone?.message} disabled={!podeEditarTudo} {...register('telefone')}
              onChange={(e) => setValue('telefone', formatarTelefone(e.target.value), { shouldValidate: true })} />
            <CampoData id="dataNascimento" label="DATA DE NASCIMENTO"
              value={dataNascimentoAtual} erro={errors.dataNascimento?.message} disabled={!podeEditarTudo}
              onChange={(v) => setValue('dataNascimento', v, { shouldValidate: true })} />
            <Select id="estadoCivil" label="ESTADO CIVIL" placeholder="Selecione"
              options={ESTADO_CIVIL_OPTIONS} error={errors.estadoCivil?.message}
              disabled={!podeEditarTudo} {...register('estadoCivil')} />
          </div>
          <StatusCards label="SEXO" options={SEXO_OPTIONS}
            selecionado={sexoAtual} disabled={!podeEditarTudo} {...register('sexo')} />
        </section>

        {/* ─── Localização ──────────────────────────────────── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><MapPin size={20} /></span>
            <h2 className={styles.secaoTitulo}>Localização</h2>
          </div>
          <div className={styles.grid2}>
            <div className={styles.spanFull}>
              <Input id="cep" label="CEP" placeholder="00000-000" inputMode="numeric" maxLength={9}
                error={errors.endereco?.cep?.message} disabled={!podeEditarTudo}
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
                error={errors.endereco?.logradouro?.message} disabled={!podeEditarTudo}
                {...register('endereco.logradouro')} />
            </div>
            <Input id="numero" label="NÚMERO" placeholder="123, s/n…"
              error={errors.endereco?.numero?.message} disabled={!podeEditarTudo}
              {...register('endereco.numero')} />
            <Input id="complemento" label="COMPLEMENTO" placeholder="Apto, bloco…"
              error={errors.endereco?.complemento?.message} disabled={!podeEditarTudo}
              {...register('endereco.complemento')} />
            <Input id="bairro" label="BAIRRO" list="lista-bairros"
              error={errors.endereco?.bairro?.message} disabled={!podeEditarTudo}
              {...register('endereco.bairro')} />
            <datalist id="lista-bairros">
              {bairros?.map((b) => <option key={b} value={b} />)}
            </datalist>
            <Input id="cidade" label="CIDADE"
              error={errors.endereco?.cidade?.message} disabled={!podeEditarTudo}
              {...register('endereco.cidade')} />
            <Select id="uf" label="UF" placeholder="UF"
              options={UF_OPTIONS} error={errors.endereco?.uf?.message}
              disabled={!podeEditarTudo} {...register('endereco.uf')} />
          </div>
        </section>

        {/* ─── Dados da igreja ──────────────────────────────── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><Church size={20} /></span>
            <h2 className={styles.secaoTitulo}>Dados da igreja</h2>
          </div>

          <StatusCards label="VÍNCULO" options={VINCULO_OPTIONS}
            selecionado={vinculoAtual} disabled={!podeEditarTudo} {...register('vinculo')} />

          {vinculoAtual === 'MEMBRO' && (
            <div className={styles.batismoWrap}>
              <CampoData id="dataBatismo" label="DATA DE BATISMO"
                value={dataBatismoAtual} erro={errors.dataBatismo?.message} disabled={!podeEditarTudo}
                onChange={(v) => setValue('dataBatismo', v, { shouldValidate: true })} />
              <span className={styles.campoHint}>Opcional</span>
            </div>
          )}

          <div className={styles.ministerioWrap}>
            <span className={styles.labelMinisterio}>CARGO</span>
            <Input id="cargo" placeholder="Ex: Pastor, Missionário, Secretário…"
              error={errors.cargo?.message} disabled={!podeEditarTudo}
              {...register('cargo')} />
          </div>
        </section>

        {/* ─── Observações ──────────────────────────────────── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><FileText size={20} /></span>
            <h2 className={styles.secaoTitulo}>Observações</h2>
          </div>
          <div className={styles.campoTextarea}>
            <label className={styles.labelTextarea} htmlFor="observacoes">NOTAS ADICIONAIS</label>
            <textarea id="observacoes" className={styles.textarea} disabled={!podeEditarTudo}
              placeholder="Informações relevantes…" {...register('observacoes')} />
          </div>
        </section>

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

        {/* Foto salva sozinha ao trocar; quem não edita o resto não tem o que salvar aqui. */}
        {podeEditarTudo && (
          <Button type="submit" variant="primary" isLoading={isLoading} disabled={isLoading}>
            Salvar alterações
          </Button>
        )}
      </form>

      <AlterarSenhaForm />

      {termosAceitosEm && (
        <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
          Termos aceitos em{' '}
          {new Date(termosAceitosEm).toLocaleDateString('pt-BR', {
            day: '2-digit', month: '2-digit', year: 'numeric',
          })}
        </p>
      )}
    </div>
  )
}
