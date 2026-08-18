'use client'

import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Landmark, Info, ShieldCheck, Save, RotateCcw, AlertTriangle } from 'lucide-react'
import { useMinhaIgreja, useAtualizarIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { ModalExcluirIgreja } from '@/components/module/configuracoes/ModalExcluirIgreja/ModalExcluirIgreja'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { SkeletonIgrejaConfig } from './SkeletonIgrejaConfig'
import styles from '../configuracoes.module.css'

const schema = z.object({
  nome: z.string().min(1, 'O nome da igreja é obrigatório.').max(255),
  razaoSocial: z.string().max(255).optional(),
  cnpj: z.string().max(18).optional(),
  denominacao: z.string().max(255).optional(),
  sigla: z.string().max(20).optional(),
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

// Nome e e-mail ficam de fora: já obrigatórios no cadastro, inflariam o percentual à toa.
const CAMPOS_COMPLETUDE: (keyof FormData)[] = [
  'razaoSocial', 'cnpj', 'denominacao', 'sigla', 'telefoneContato',
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

  const role = useAuthStore((s) => s.role)
  const exclusaoAgendadaEm = useAuthStore((s) => s.exclusaoAgendadaEm)
  const diasRestantes = useAuthStore((s) => s.diasRestantes)
  const atualizarExclusaoAgendada = useAuthStore((s) => s.atualizarExclusaoAgendada)
  const [modalExcluirAberto, setModalExcluirAberto] = useState(false)
  const [cancelandoExclusao, setCancelandoExclusao] = useState(false)

  async function cancelarExclusao() {
    setCancelandoExclusao(true)
    try {
      await api.post(Endpoints.igreja.exclusao.CANCELAR)
      atualizarExclusaoAgendada(null, null)
      notificar.sucesso('Exclusão cancelada', 'A igreja não será mais excluída.')
    } catch (erro: unknown) {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível cancelar a exclusão', mensagem)
    } finally {
      setCancelandoExclusao(false)
    }
  }

  // O logo não faz parte do schema do RHF (não é um <input> comum, é o UploadFoto) — vive
  // em estado próprio, espelhado da igreja como o resto do formulário via reset().
  const [logoFotoId, setLogoFotoId] = useState<string | null>(null)
  const [logoAlterada, setLogoAlterada] = useState(false)

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
      sigla: igreja.sigla ?? '',
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
    setLogoFotoId(igreja.logoFotoId ?? null)
    setLogoAlterada(false)
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
      sigla: data.sigla?.trim().toUpperCase() || null,
      emailContato: data.emailContato,
      telefoneContato: data.telefoneContato || null,
      logoFotoId,
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
    return <SkeletonIgrejaConfig />
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
          <div className={styles.logoWrap}>
            <UploadFoto
              valor={logoFotoId}
              onChange={(id) => { setLogoFotoId(id); setLogoAlterada(true) }}
              formato="circulo"
              nomeFallback={igreja.nome}
            />
          </div>

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
              <label className={styles.rotulo} htmlFor="sigla">Sigla</label>
              <input id="sigla" className={styles.input} placeholder="Ex.: IBC, SIBAPI" maxLength={20} {...register('sigla')} />
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
              onClick={() => {
                reset()
                setLogoFotoId(igreja?.logoFotoId ?? null)
                setLogoAlterada(false)
              }}
              disabled={(!isDirty && !logoAlterada) || atualizar.isPending}
            >
              <RotateCcw size={16} aria-hidden="true" />
              Descartar alterações
            </button>

            <button
              type="submit"
              className={styles.botaoPrimario}
              disabled={(!isDirty && !logoAlterada) || atualizar.isPending}
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

      {role === 'ADMIN_IGREJA' && (
        <section className={styles.zonaPerigo}>
          <h2 className={styles.tituloZonaPerigo}>
            <AlertTriangle size={16} aria-hidden="true" />
            Zona de Perigo
          </h2>

          {exclusaoAgendadaEm ? (
            <div className={styles.linhaZonaPerigo}>
              <p className={styles.textoZonaPerigo}>
                Esta igreja será excluída definitivamente em {diasRestantes} dia(s).
              </p>
              <button
                type="button"
                className={styles.botaoSecundario}
                onClick={cancelarExclusao}
                disabled={cancelandoExclusao}
              >
                {cancelandoExclusao ? 'Cancelando...' : 'Cancelar exclusão'}
              </button>
            </div>
          ) : (
            <div className={styles.linhaZonaPerigo}>
              <p className={styles.textoZonaPerigo}>
                Excluir a igreja apaga todos os dados definitivamente, após 10 dias de carência.
              </p>
              <button
                type="button"
                className={styles.botaoPerigo}
                onClick={() => setModalExcluirAberto(true)}
              >
                Excluir esta igreja
              </button>
            </div>
          )}
        </section>
      )}

      {modalExcluirAberto && (
        <ModalExcluirIgreja
          nomeIgreja={igreja.nome}
          onClose={() => setModalExcluirAberto(false)}
          onExcluidoComSucesso={() => {
            setModalExcluirAberto(false)
            atualizarExclusaoAgendada(new Date().toISOString(), 10)
          }}
        />
      )}
    </>
  )
}
