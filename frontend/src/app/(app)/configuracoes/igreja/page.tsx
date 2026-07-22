'use client'

import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Landmark, Info, ShieldCheck, Save, RotateCcw } from 'lucide-react'
import { useMinhaIgreja, useAtualizarIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import styles from '../configuracoes.module.css'

const schema = z.object({
  nome: z.string().min(1, 'O nome da igreja é obrigatório.').max(255),
  razaoSocial: z.string().max(255).optional(),
  cnpj: z.string().max(18).optional(),
  denominacao: z.string().max(255).optional(),
  emailContato: z.string().min(1, 'O e-mail de contato é obrigatório.').email('E-mail inválido.'),
  telefoneContato: z.string().max(50).optional(),
  cep: z.string().max(9).optional(),
  logradouro: z.string().max(255).optional(),
  numero: z.string().max(20).optional(),
  complemento: z.string().max(255).optional(),
  bairro: z.string().max(255).optional(),
  cidade: z.string().max(255).optional(),
  uf: z.string().max(2, 'Use a sigla de 2 letras.').optional(),
})

type FormData = z.infer<typeof schema>

/**
 * Os campos que contam para a barra de completude. Nome e e-mail ficam de fora de
 * propósito: são obrigatórios desde o cadastro, então sempre estariam preenchidos e só
 * inflariam o percentual sem dizer nada.
 */
const CAMPOS_COMPLETUDE: (keyof FormData)[] = [
  'razaoSocial', 'cnpj', 'denominacao', 'telefoneContato',
  'cep', 'logradouro', 'numero', 'bairro', 'cidade', 'uf',
]

function formatarDataHora(iso: string | null): string {
  if (!iso) return '—'
  const data = new Date(iso)
  return data.toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function DadosDaIgrejaPage() {
  const { data: igreja, isLoading } = useMinhaIgreja()
  const atualizar = useAtualizarIgreja()
  const { buscar: buscarCep, carregando: buscandoCep } = useBuscaCep()

  const {
    register, handleSubmit, reset, watch, setValue,
    formState: { errors, isDirty },
  } = useForm<FormData>({ resolver: zodResolver(schema) })

  // Quando a igreja chega (ou volta do salvar), o formulário vira o espelho dela.
  useEffect(() => {
    if (!igreja) return
    reset({
      nome: igreja.nome ?? '',
      razaoSocial: igreja.razaoSocial ?? '',
      cnpj: igreja.cnpj ?? '',
      denominacao: igreja.denominacao ?? '',
      emailContato: igreja.emailContato ?? '',
      telefoneContato: igreja.telefoneContato ?? '',
      cep: igreja.endereco?.cep ?? '',
      logradouro: igreja.endereco?.logradouro ?? '',
      numero: igreja.endereco?.numero ?? '',
      complemento: igreja.endereco?.complemento ?? '',
      bairro: igreja.endereco?.bairro ?? '',
      cidade: igreja.endereco?.cidade ?? '',
      uf: igreja.endereco?.uf ?? '',
    })
  }, [igreja, reset])

  const valores = watch()
  const preenchidos = CAMPOS_COMPLETUDE.filter((c) => !!valores[c]?.trim()).length
  const completude = Math.round((preenchidos / CAMPOS_COMPLETUDE.length) * 100)

  async function aoSairDoCep(cep: string) {
    const encontrado = await buscarCep(cep)
    if (!encontrado) return
    // Não sobrescreve o que a pessoa já digitou — o ViaCEP só preenche o que está vazio.
    if (!valores.logradouro) setValue('logradouro', encontrado.logradouro ?? '', { shouldDirty: true })
    if (!valores.bairro) setValue('bairro', encontrado.bairro ?? '', { shouldDirty: true })
    if (!valores.cidade) setValue('cidade', encontrado.cidade ?? '', { shouldDirty: true })
    if (!valores.uf) setValue('uf', encontrado.uf ?? '', { shouldDirty: true })
  }

  function aoSalvar(data: FormData) {
    atualizar.mutate({
      nome: data.nome,
      razaoSocial: data.razaoSocial || null,
      cnpj: data.cnpj || null,
      denominacao: data.denominacao || null,
      emailContato: data.emailContato,
      telefoneContato: data.telefoneContato || null,
      logoUrl: igreja?.logoUrl ?? null,
      endereco: {
        cep: data.cep || undefined,
        logradouro: data.logradouro || undefined,
        numero: data.numero || undefined,
        complemento: data.complemento || undefined,
        bairro: data.bairro || undefined,
        cidade: data.cidade || undefined,
        uf: data.uf || undefined,
      },
    })
  }

  if (isLoading || !igreja) {
    return <div className={styles.skeleton} aria-label="Carregando dados da igreja" />
  }

  return (
    <>
      <div className={styles.colunas}>
        <aside className={styles.cardInstitucional}>
          <div className={styles.iconeInstitucional}>
            <Landmark size={28} aria-hidden="true" />
          </div>
          <h2 className={styles.tituloInstitucional}>Dados Institucionais</h2>
          <p className={styles.textoInstitucional}>
            Mantenha os dados da sua igreja atualizados para garantir que as comunicações e
            documentos sejam gerados corretamente.
          </p>

          <div className={styles.listaInstitucional}>
            <p className={styles.itemInstitucional}>
              <Info size={14} aria-hidden="true" />
              CNPJ e razão social são necessários para emissão de documentos fiscais.
            </p>
            <p className={styles.itemInstitucional}>
              <ShieldCheck size={14} aria-hidden="true" />
              Só administradores da igreja podem ver e alterar estes dados.
            </p>
          </div>
        </aside>

        <form className={styles.cartao} onSubmit={handleSubmit(aoSalvar)} noValidate>
          <div className={styles.grade}>
            <div className={`${styles.campo} ${styles.campoLargo}`}>
              <label className={styles.rotulo} htmlFor="nome">Nome da igreja</label>
              <input id="nome" className={styles.input} {...register('nome')} />
              {errors.nome && <span className={styles.erroCampo}>{errors.nome.message}</span>}
            </div>

            <div className={`${styles.campo} ${styles.campoLargo}`}>
              <label className={styles.rotulo} htmlFor="razaoSocial">Razão social</label>
              <input id="razaoSocial" className={styles.input} {...register('razaoSocial')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="cnpj">CNPJ</label>
              <input id="cnpj" className={styles.input} placeholder="00.000.000/0000-00" {...register('cnpj')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="denominacao">Denominação</label>
              <input id="denominacao" className={styles.input} placeholder="Ex.: Assembleia de Deus" {...register('denominacao')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="telefoneContato">Telefone de contato</label>
              <input id="telefoneContato" className={styles.input} placeholder="+55 (11) 98765-4321" {...register('telefoneContato')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="emailContato">E-mail de contato</label>
              <input id="emailContato" type="email" className={styles.input} {...register('emailContato')} />
              {errors.emailContato && <span className={styles.erroCampo}>{errors.emailContato.message}</span>}
            </div>
          </div>

          <div className={styles.separador} />
          <h3 className={styles.subtituloSecao}>Endereço</h3>

          <div className={styles.grade}>
            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="cep">CEP</label>
              <input
                id="cep"
                className={styles.input}
                placeholder="00000-000"
                disabled={buscandoCep}
                {...register('cep', { onBlur: (e) => aoSairDoCep(e.target.value) })}
              />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="logradouro">Logradouro</label>
              <input id="logradouro" className={styles.input} {...register('logradouro')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="numero">Número</label>
              <input id="numero" className={styles.input} {...register('numero')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="complemento">Complemento</label>
              <input id="complemento" className={styles.input} {...register('complemento')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="bairro">Bairro</label>
              <input id="bairro" className={styles.input} {...register('bairro')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="cidade">Cidade</label>
              <input id="cidade" className={styles.input} {...register('cidade')} />
            </div>

            <div className={styles.campo}>
              <label className={styles.rotulo} htmlFor="uf">UF</label>
              <input id="uf" className={styles.input} maxLength={2} placeholder="SP" {...register('uf')} />
              {errors.uf && <span className={styles.erroCampo}>{errors.uf.message}</span>}
            </div>
          </div>

          <div className={styles.acoes}>
            <button
              type="button"
              className={styles.botaoSecundario}
              onClick={() => reset()}
              disabled={!isDirty || atualizar.isPending}
            >
              <RotateCcw size={16} aria-hidden="true" />
              Descartar alterações
            </button>

            <button
              type="submit"
              className={styles.botaoPrimario}
              disabled={!isDirty || atualizar.isPending}
            >
              <Save size={16} aria-hidden="true" />
              {atualizar.isPending ? 'Salvando...' : 'Salvar alterações'}
            </button>
          </div>
        </form>
      </div>

      <div className={styles.cardsRodape}>
        <section className={styles.cardRodape}>
          <h2 className={styles.tituloRodape}>Logs de atividade</h2>
          <div className={styles.linhaLog}>
            <span>Última alteração</span>
            <span className={styles.valorLog}>{formatarDataHora(igreja.atualizadoEm)}</span>
          </div>
          <div className={styles.linhaLog}>
            <span>Alterado por</span>
            <span className={styles.valorLog}>{igreja.atualizadoPorNome ?? '—'}</span>
          </div>
        </section>

        <section className={styles.cardRodape}>
          <h2 className={styles.tituloRodape}>Completude do cadastro</h2>
          <div
            className={styles.barraCompletude}
            role="progressbar"
            aria-valuenow={completude}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Completude do cadastro da igreja"
          >
            {CAMPOS_COMPLETUDE.map((campo, i) => (
              <span
                key={campo}
                className={`${styles.segmento} ${i < preenchidos ? styles.segmentoCheio : ''}`}
              />
            ))}
          </div>
          <p className={styles.textoCompletude}>
            {completude === 100
              ? 'Cadastro completo. Todos os dados institucionais estão preenchidos.'
              : `Nível de completude: ${completude}% — faltam ${CAMPOS_COMPLETUDE.length - preenchidos} campo(s) para chegar a 100%.`}
          </p>
        </section>
      </div>
    </>
  )
}
