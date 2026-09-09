'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { CalendarClock, FileText, MapPin, Ticket, UserCog, ClipboardCheck, Users, Building2, Repeat } from 'lucide-react'
import Link from 'next/link'
import { useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useAuthStore } from '@/store/authStore'
import { podeConectarContaPagamento } from '@/lib/permissoes'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { Button } from '@/components/common/button/Button'
import { formatarValorDigitado } from '@/lib/formats/financeiro/movimentacaoFormat'
import { formatarHoraDigitada } from '@/lib/masks'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
import { BlocoRecolhivel } from '@/components/common/BlocoRecolhivel/BlocoRecolhivel'
import { useRolarParaErro } from '@/hooks/forms/useRolarParaErro'
import { PreviaEvento } from './PreviaEvento'
import { SeletorLocal } from './SeletorLocal'
import { SeletorResponsavel } from './SeletorResponsavel'
import { BlocoParaQuemE } from './BlocoParaQuemE'
import { ModalImpactoRestricao } from './ModalImpactoRestricao'
import { ModalImpactoMudancaPreco } from './ModalImpactoMudancaPreco'
import { ModalEscopoEdicaoEvento } from './ModalEscopoEdicaoEvento'
import { CamposPersonalizadosPainel } from './CamposPersonalizadosPainel'
import type { CamposPersonalizadosHandle } from './CamposPersonalizadosPainel'
import { useTiposEvento } from '@/hooks/evento/useTiposEvento'
import { useAtualizarFotoEvento } from '@/hooks/evento/useAtualizarFotoEvento'
import { notificar } from '@/components/common/Notificacao/notificar'
import { OverlayCarregando } from '@/components/common/OverlayCarregando/OverlayCarregando'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { Revelar } from '@/components/common/Transicao/Revelar'
import styles from './EventoForm.module.css'
import type { UseFormReturn } from 'react-hook-form'
import type { EventoFormInput, EventoFormData } from '@/lib/validators'
import type { InscritoImpactado, ImpactoMudancaPrecoResponse, RestricaoEstadoCivil, RestricaoSexo, EscopoEdicaoEvento } from '@/types/evento.type'

type EventoFormProps = UseFormReturn<EventoFormInput, unknown, EventoFormData> & {
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  eventoId?: string
  responsaveisIniciais?: { id: string; nome: string }[]
  onSubmit: (data: EventoFormData) => void
  registrarSalvarCamposPersonalizados: (fn: ((eventoId: string) => Promise<void>) | null) => void
  impactoAfetados: InscritoImpactado[] | null
  isVerificandoImpacto: boolean
  onConfirmarImpacto: (cancelarNaoElegiveis: boolean) => void
  onFecharImpacto: () => void
  impactoMudancaPreco: ImpactoMudancaPrecoResponse | null
  onConfirmarMudancaPreco: () => void
  onFecharMudancaPreco: () => void
  aguardandoEscopoEdicao: boolean
  onEscolherEscopoEdicao: (escopo: EscopoEdicaoEvento) => void
  onFecharEscopoEdicao: () => void
}

const DIAS_SEMANA_OPTIONS = [
  { value: 'SEGUNDA', label: 'Seg' },
  { value: 'TERCA', label: 'Ter' },
  { value: 'QUARTA', label: 'Qua' },
  { value: 'QUINTA', label: 'Qui' },
  { value: 'SEXTA', label: 'Sex' },
  { value: 'SABADO', label: 'Sáb' },
  { value: 'DOMINGO', label: 'Dom' },
]

export function EventoForm(props: EventoFormProps) {
  const router = useRouter()
  const {
    register, handleSubmit, watch, setValue, setError,
    formState: { errors, touchedFields, isSubmitted },
    erroGeral, isLoading, onSubmit, ehEdicao, eventoId, responsaveisIniciais,
    registrarSalvarCamposPersonalizados,
    impactoAfetados, isVerificandoImpacto, onConfirmarImpacto, onFecharImpacto,
    impactoMudancaPreco, onConfirmarMudancaPreco, onFecharMudancaPreco,
    aguardandoEscopoEdicao, onEscolherEscopoEdicao, onFecharEscopoEdicao,
  } = props

  const { congregacao, concordar } = useRotulos()
  const { data: contaPagamento } = useContaPagamento()
  const role = useAuthStore((s) => s.role)
  const podeConectar = podeConectarContaPagamento(role)
  const repetir = watch('repetir')
  const recorrenciaFrequencia = watch('recorrenciaFrequencia')
  const recorrenciaDiasSemana = (watch('recorrenciaDiasSemana') as string[]) ?? []
  const recorrenciaFimTipo = watch('recorrenciaFimTipo')
  const recorrenciaIntervaloValor = Number(watch('recorrenciaIntervalo')) || 1
  const noPlural = recorrenciaIntervaloValor !== 1
  const requerInscricao = watch('requerInscricao')
  const tipoInscricao = watch('tipoInscricao')
  const exclusivoMembros = watch('exclusivoMembros')
  const inicioData = (watch('inicioData') as string) ?? ''
  const fimData = (watch('fimData') as string) ?? ''
  const preco = (watch('preco') as string) ?? ''
  const precisaConectarContaPagamento = requerInscricao && tipoInscricao === 'PAGO'
    && !!contaPagamento && !contaPagamento.conectada
  const fotoIdAtual = watch('fotoId') as string | null | undefined
  const localIdAtual = watch('localId') as string | undefined
  const localTextoAtual = watch('localTexto') as string | undefined
  const enderecoLocalAtual = watch('enderecoLocal') as import('@/types/pessoa.type').Endereco | undefined
  const novoLocalAtual = watch('novoLocal') as import('@/types/evento.type').LocalEventoRequest | undefined
  const tipoAtual = (watch('tipo') as string) ?? ''
  const responsavelIdsAtual = (watch('responsavelPessoaIds') as string[] | undefined) ?? []
  const vagasAtual = watch('vagas') as number | undefined
  const recorteEtarioAtual = watch('recorteEtario') as string | null | undefined
  const idadeMinAtual = watch('idadeMin') as number | undefined
  const idadeMaxAtual = watch('idadeMax') as number | undefined
  const restricaoEstadoCivilAtual = watch('restricaoEstadoCivil') as RestricaoEstadoCivil | null | undefined
  const restricaoSexoAtual = watch('restricaoSexo') as RestricaoSexo | null | undefined

  const { data: tiposSugeridos = [] } = useTiposEvento()
  const atualizarFoto = useAtualizarFotoEvento(eventoId)

  const { data: vinculoStatus } = useVinculoStatus()
  const temFamilia = vinculoStatus != null && vinculoStatus.estado !== 'INDEPENDENTE'

  // "Restringir quem pode participar": aberto acompanha "tem restrição ativa" até a
  // pessoa mexer no bloco à mão — aí a escolha manual manda. Ajuste em fase de render
  // (o eslint proíbe setState síncrono dentro de effect).
  const [restringirAbertoManual, setRestringirAbertoManual] = useState<boolean | null>(null)
  const temRestricaoAtiva = !!exclusivoMembros || idadeMinAtual != null || idadeMaxAtual != null
    || restricaoEstadoCivilAtual != null || restricaoSexoAtual != null
  const restringirAberto = restringirAbertoManual ?? temRestricaoAtiva

  // Um botão só ("Salvar alterações") salva evento + campos personalizados — sem isso,
  // existiam dois botões de salvar na mesma tela, confuso qual fazia o quê. O painel pode
  // existir ANTES do evento ter id (evento novo) — por isso quem chama `salvar()` de
  // verdade é o próprio useEventoForm (registrado aqui), depois de criar/atualizar o
  // evento e já sabendo o id definitivo.
  const camposPersonalizadosRef = useRef<CamposPersonalizadosHandle>(null)
  const [erroValidacao, setErroValidacao] = useState<string | null>(null)

  const formRef = useRef<HTMLFormElement>(null)
  const { rolarParaErro } = useRolarParaErro(formRef)

  // Required ainda vazios — só entram na prévia depois que a pessoa interagiu
  // (submeteu ou tocou o campo), pra não acusar erro na cara de quem acabou de abrir.
  const rotulosRequired: Record<'titulo' | 'inicioData' | 'inicioHora', string> = {
    titulo: 'título',
    inicioData: 'data de início',
    inicioHora: 'horário de início',
  }
  const camposFaltando = (Object.keys(rotulosRequired) as Array<keyof typeof rotulosRequired>)
    .filter((campo) => {
      const valor = watch(campo)
      const vazio = valor == null || String(valor).trim() === ''
      const jaInteragiu = isSubmitted || touchedFields[campo]
      return vazio && jaInteragiu
    })
    .map((campo) => rotulosRequired[campo])

  const localResumo =
    novoLocalAtual?.nome?.trim() ||
    localTextoAtual?.trim() ||
    enderecoLocalAtual?.cidade?.trim() ||
    ''

  const rotuloOutrasCongregacoes =
    `${concordar(congregacao.genero, 'os_min')} outras ${congregacao.plural.toLowerCase()}`

  useEffect(() => {
    registrarSalvarCamposPersonalizados((eventoIdSalvo) => (
      camposPersonalizadosRef.current?.salvar(eventoIdSalvo) ?? Promise.resolve()
    ))
    return () => registrarSalvarCamposPersonalizados(null)
  }, [registrarSalvarCamposPersonalizados])

  return (
    <form ref={formRef} className={styles.form} onSubmit={(e) => handleSubmit(
      (data) => {
        setErroValidacao(null)
        if (precisaConectarContaPagamento) {
          setError('preco', {
            type: 'manual',
            message: 'Conecte uma conta de recebimento antes de publicar um evento pago.',
          })
          setErroValidacao('Faltou preencher um campo — te levei até ele.')
          rolarParaErro()
          return
        }
        onSubmit(data)
      },
      () => {
        setErroValidacao('Faltou preencher um campo — te levei até ele.')
        rolarParaErro()
      },
    )(e)}>
      <div className={styles.colunas}>
        {/* ─── 1 · Sobre o evento ─── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><FileText size={20} /></span>
            <h2 className={styles.secaoTitulo}>Sobre o evento</h2>
          </div>
          <div className={styles.campos}>
            <Input
              id="titulo"
              label="TÍTULO DO EVENTO*"
              placeholder="Ex: Culto de Celebração"
              error={errors.titulo?.message}
              {...register('titulo')}
            />
            <div>
              <InputComSugestoes
                id="tipo"
                label="TIPO DO EVENTO"
                placeholder="Ex: Culto, Retiro, Conferência…"
                sugestoes={tiposSugeridos}
                value={tipoAtual}
                error={errors.tipo?.message}
                registerProps={register('tipo')}
                onSelecionarSugestao={(v) => setValue('tipo', v, { shouldDirty: true })}
              />
            </div>

            <div className={styles.campoTextarea}>
              <label className={styles.labelTextarea} htmlFor="descricao">DESCRIÇÃO</label>
              <textarea
                id="descricao"
                className={styles.textarea}
                placeholder="Descreva os detalhes do evento..."
                {...register('descricao')}
              />
            </div>

            <div className={styles.imagemWrap}>
              <span className={styles.labelData}>IMAGEM DO EVENTO</span>
              <UploadFoto
                valor={fotoIdAtual}
                onChange={(id) => {
                  setValue('fotoId', id, { shouldValidate: true })
                  // Em criação, o evento ainda não existe (sem id) — a foto só é enviada
                  // junto do "Salvar evento". Em edição, salva sozinha ao confirmar o recorte.
                  if (!ehEdicao || !eventoId) return
                  const fotoAnterior = fotoIdAtual ?? null
                  atualizarFoto.mutate(id, {
                    onSuccess: () => notificar.sucesso(id ? 'Imagem atualizada.' : 'Imagem removida.'),
                    onError: (erro: unknown) => {
                      setValue('fotoId', fotoAnterior)
                      const mensagem =
                        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                        'Tente novamente em alguns instantes.'
                      notificar.erro('Não foi possível salvar a imagem', mensagem)
                    },
                  })
                }}
                formato="banner"
              />
            </div>
          </div>
        </section>

        {/* ─── 2 · Quando e onde ─── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><CalendarClock size={20} /></span>
            <h2 className={styles.secaoTitulo}>Quando e onde</h2>
          </div>

          <div className={styles.campos}>
            {/* Início */}
            <div className={styles.grupoData}>
              <span className={styles.labelData}>INÍCIO*</span>
              <div className={styles.linhaDataHora}>
                <div className={styles.campoDataWrap}>
                  <CampoData
                    id="inicio-data"
                    label="Data"
                    value={inicioData}
                    onChange={(v) => setValue('inicioData', v, { shouldValidate: true })}
                    erro={errors.inicioData?.message}
                  />
                </div>
                <div className={styles.campoHoraWrap}>
                  <span className={styles.subLabel}>Horário</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    placeholder="hh:mm"
                    maxLength={5}
                    className={styles.inputData}
                    aria-label="Horário de início"
                    {...register('inicioHora')}
                    onChange={(e) => setValue('inicioHora', formatarHoraDigitada(e.target.value), { shouldValidate: true })}
                  />
                  {errors.inicioHora && <span className={styles.erroCampo} data-campo-erro>{errors.inicioHora.message}</span>}
                </div>
              </div>
            </div>

            {/* Término */}
            <div className={styles.grupoData}>
              <span className={styles.labelData}>
                TÉRMINO <span className={styles.opcional}>(opcional)</span>
              </span>
              <div className={styles.linhaDataHora}>
                <div className={styles.campoDataWrap}>
                  <CampoData
                    id="fim-data"
                    label="Data"
                    value={fimData}
                    onChange={(v) => setValue('fimData', v, { shouldValidate: true })}
                    erro={errors.fimData?.message}
                  />
                </div>
                <div className={styles.campoHoraWrap}>
                  <span className={styles.subLabel}>Horário</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    placeholder="hh:mm"
                    maxLength={5}
                    className={styles.inputData}
                    aria-label="Horário de término"
                    {...register('fimHora')}
                    onChange={(e) => setValue('fimHora', formatarHoraDigitada(e.target.value), { shouldValidate: true })}
                  />
                  {errors.fimHora && <span className={styles.erroCampo} data-campo-erro>{errors.fimHora.message}</span>}
                </div>
              </div>
            </div>

            {/* Local */}
            <div className={styles.grupoData}>
              <span className={styles.labelData}>
                <MapPin size={14} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
                LOCAL
              </span>
              <SeletorLocal
                localId={localIdAtual}
                localTexto={localTextoAtual}
                enderecoLocal={enderecoLocalAtual}
                ehEdicao={ehEdicao}
                novoLocal={novoLocalAtual}
                error={errors.localId?.message ?? errors.localTexto?.message ?? errors.enderecoLocal?.message}
                errosEndereco={{
                  cep: errors.enderecoLocal?.cep?.message,
                  logradouro: errors.enderecoLocal?.logradouro?.message,
                  numero: errors.enderecoLocal?.numero?.message,
                  complemento: errors.enderecoLocal?.complemento?.message,
                  bairro: errors.enderecoLocal?.bairro?.message,
                  cidade: errors.enderecoLocal?.cidade?.message,
                  uf: errors.enderecoLocal?.uf?.message,
                }}
                onChangeLocalId={(id) => {
                  setValue('localId', id, { shouldDirty: true })
                  if (id) setValue('localTexto', undefined, { shouldDirty: true })
                }}
                onChangeLocalTexto={(texto) => {
                  setValue('localTexto', texto, { shouldDirty: true })
                  if (texto) setValue('localId', undefined, { shouldDirty: true })
                }}
                onChangeEnderecoLocal={(e) => setValue('enderecoLocal', e, { shouldDirty: true, shouldValidate: true })}
                onChangeNovoLocal={(p) => setValue('novoLocal', p, { shouldDirty: true, shouldValidate: true })}
                onCapacidadeSugerida={(cap) => {
                  if (requerInscricao && vagasAtual == null) {
                    setValue('vagas', cap, { shouldDirty: true, shouldValidate: true })
                  }
                }}
              />
            </div>
          </div>

          {/* Repetição — só faz sentido no cadastro; editar uma ocorrência existente usa o
              seletor de escopo (só esta/esta e as seguintes/toda a série), não este bloco. */}
          {!ehEdicao && (
            <BlocoRecolhivel
              id="repetir"
              titulo="Repetir este evento"
              descricao="As próximas datas entram na agenda sozinhas."
              icone={<Repeat size={18} />}
              aberto={!!repetir}
              onToggle={(v) => {
                setValue('repetir', v, { shouldValidate: true })
                if (!v) {
                  setValue('recorrenciaFrequencia', undefined)
                  setValue('recorrenciaDiasSemana', [])
                  setValue('recorrenciaFimTipo', 'NUNCA')
                }
              }}
            >
              <div className={styles.campos}>
                <div>
                  <span className={styles.labelData}>REPETE A CADA</span>
                  <div className={styles.linhaRecorrencia}>
                    <Input id="recorrencia-intervalo" type="number" min={1}
                      placeholder="1"
                      aria-label="Intervalo de repetição, em número"
                      error={errors.recorrenciaIntervalo?.message}
                      {...register('recorrenciaIntervalo')} />
                    <Select id="recorrencia-frequencia" placeholder="dia, semana ou mês"
                      aria-label="Unidade de repetição: dia, semana ou mês"
                      options={[
                        { value: 'DIARIA', label: noPlural ? 'dias' : 'dia' },
                        { value: 'SEMANAL', label: noPlural ? 'semanas' : 'semana' },
                        { value: 'MENSAL', label: noPlural ? 'meses' : 'mês' },
                      ]}
                      error={errors.recorrenciaFrequencia?.message}
                      {...register('recorrenciaFrequencia')} />
                  </div>
                  <span className={styles.exemploRecorrencia}>
                    {recorrenciaFrequencia
                      ? `Ex.: repete a cada ${recorrenciaIntervaloValor} ${
                          recorrenciaFrequencia === 'DIARIA' ? (noPlural ? 'dias' : 'dia')
                            : recorrenciaFrequencia === 'SEMANAL' ? (noPlural ? 'semanas' : 'semana')
                            : (noPlural ? 'meses' : 'mês')
                        }.`
                      : 'Ex.: a cada 1 semana repete toda semana; a cada 2 semanas, uma sim uma não.'}
                  </span>
                </div>

                {recorrenciaFrequencia === 'SEMANAL' && (
                  <Transicao modo="subir">
                    <span className={styles.labelData}>DIAS DA SEMANA</span>
                    <div className={styles.chipsLinha}>
                      {DIAS_SEMANA_OPTIONS.map((dia) => {
                        const marcado = recorrenciaDiasSemana.includes(dia.value)
                        return (
                          <button
                            key={dia.value}
                            type="button"
                            className={marcado ? styles.chipDiaAtivo : styles.chipDia}
                            onClick={() => {
                              const novos = marcado
                                ? recorrenciaDiasSemana.filter((d) => d !== dia.value)
                                : [...recorrenciaDiasSemana, dia.value]
                              setValue('recorrenciaDiasSemana', novos, { shouldValidate: true })
                            }}
                          >
                            {dia.label}
                          </button>
                        )
                      })}
                    </div>
                    {errors.recorrenciaDiasSemana && (
                      <span className={styles.erroCampo} data-campo-erro>{errors.recorrenciaDiasSemana.message}</span>
                    )}
                  </Transicao>
                )}

                {recorrenciaFrequencia === 'MENSAL' && (
                  <Transicao modo="subir">
                    <Select id="recorrencia-tipo-mensal" label="REPETE" placeholder="Selecione"
                      options={[
                        { value: 'DIA_FIXO', label: 'No mesmo dia do mês' },
                        { value: 'DIA_DA_SEMANA', label: 'Na mesma posição (ex.: toda 1ª terça)' },
                      ]}
                      error={errors.recorrenciaTipoMensal?.message}
                      {...register('recorrenciaTipoMensal')} />
                  </Transicao>
                )}

                <Select id="recorrencia-fim-tipo" label="TERMINA" placeholder="Selecione"
                  options={[
                    { value: 'NUNCA', label: 'Nunca' },
                    { value: 'DATA', label: 'Em uma data' },
                    { value: 'CONTAGEM', label: 'Depois de um número de vezes' },
                  ]}
                  error={errors.recorrenciaFimTipo?.message}
                  {...register('recorrenciaFimTipo')} />

                {recorrenciaFimTipo === 'DATA' && (
                  <Transicao modo="subir">
                    <CampoData
                      id="recorrencia-data-fim"
                      label="Data final"
                      value={(watch('recorrenciaDataFim') as string) ?? ''}
                      onChange={(v) => setValue('recorrenciaDataFim', v, { shouldValidate: true })}
                      erro={errors.recorrenciaDataFim?.message}
                    />
                  </Transicao>
                )}

                {recorrenciaFimTipo === 'CONTAGEM' && (
                  <Transicao modo="subir">
                    <Input id="recorrencia-numero-ocorrencias" label="NÚMERO DE OCORRÊNCIAS" type="number" min={1}
                      placeholder="Ex.: 10"
                      error={errors.recorrenciaNumeroOcorrencias?.message}
                      {...register('recorrenciaNumeroOcorrencias')} />
                  </Transicao>
                )}
              </div>
            </BlocoRecolhivel>
          )}
        </section>

        {/* ─── 3 · Organização ─── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><UserCog size={20} /></span>
            <h2 className={styles.secaoTitulo}>Organização</h2>
          </div>
          <SeletorResponsavel
            ids={responsavelIdsAtual}
            iniciais={responsaveisIniciais}
            onChange={(lista) => setValue('responsavelPessoaIds', lista, { shouldDirty: true })}
          />

          {temFamilia && (
            <label className={styles.toggleRow}>
              <span className={styles.toggleTexto}>
                <span className={styles.toggleTitulo}>
                  <Building2 size={16} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
                  Só minha igreja
                </span>
                <span className={styles.toggleDescricao}>
                  Não mostra este evento para {concordar(congregacao.genero, 'os_min')} outras {congregacao.plural.toLowerCase()}.
                </span>
              </span>
              <span className={styles.switch}>
                <input type="checkbox" className={styles.switchInput} {...register('restritoPropriaIgreja')} />
                <span className={styles.switchTrilho} />
              </span>
            </label>
          )}
        </section>

        {/* ─── Restringir quem pode participar (recolhido por padrão) ─── */}
        <BlocoRecolhivel
          id="restringir-publico"
          titulo="Restringir quem pode participar"
          descricao="Idade, estado civil, sexo ou só membros"
          icone={<Users size={18} />}
          aberto={restringirAberto}
          onToggle={(v) => setRestringirAbertoManual(v)}
        >
          <BlocoParaQuemE
            recorteEtario={recorteEtarioAtual}
            idadeMin={idadeMinAtual}
            idadeMax={idadeMaxAtual}
            restricaoEstadoCivil={restricaoEstadoCivilAtual}
            restricaoSexo={restricaoSexoAtual}
            exclusivoMembros={!!exclusivoMembros}
            erroIdadeMax={errors.idadeMax?.message}
            onChangeRecorteEtario={(v) => setValue('recorteEtario', v, { shouldDirty: true })}
            onChangeIdadeMin={(v) => setValue('idadeMin', v, { shouldDirty: true, shouldValidate: true })}
            onChangeIdadeMax={(v) => setValue('idadeMax', v, { shouldDirty: true, shouldValidate: true })}
            onChangeEstadoCivil={(v) => setValue('restricaoEstadoCivil', v, { shouldDirty: true })}
            onChangeSexo={(v) => setValue('restricaoSexo', v, { shouldDirty: true })}
            onChangeExclusivoMembros={(v) => setValue('exclusivoMembros', v, { shouldDirty: true })}
          />
        </BlocoRecolhivel>

        {/* ─── 4 · Inscrições ─── */}
        <section className={styles.secao}>
          <div className={styles.secaoHeader}>
            <span className={styles.secaoIcone}><Ticket size={20} /></span>
            <h2 className={styles.secaoTitulo}>Inscrições</h2>
          </div>

          <label className={styles.toggleRow}>
            <span className={styles.toggleTexto}>
              <span className={styles.toggleTitulo}>Exigir inscrição</span>
              <span className={styles.toggleDescricao}>
                Participantes se inscrevem antes. Você controla vagas, prazo e valor.
              </span>
            </span>
            <span className={styles.switch}>
              <input type="checkbox" className={styles.switchInput} {...register('requerInscricao')} />
              <span className={styles.switchTrilho} />
            </span>
          </label>

          {requerInscricao && (
            <Revelar className={styles.campos}>
              <div>
                <Input
                  id="vagas"
                  type="number"
                  label="VAGAS"
                  placeholder="Ex: 50"
                  min={1}
                  error={errors.vagas?.message}
                  {...register('vagas')}
                />
                <span className={styles.campoHint}>Vazio = sem limite de vagas.</span>
              </div>

              <div className={styles.grupoData}>
                <span className={styles.labelData}>
                  INSCRIÇÕES ATÉ <span className={styles.opcional}>(opcional)</span>
                </span>
                <div className={styles.linhaDataHora}>
                  <div className={styles.campoDataWrap}>
                    <CampoData
                      id="inscricoes-ate-data"
                      label="Data"
                      value={(watch('inscricoesAteData') as string) ?? ''}
                      onChange={(v) => setValue('inscricoesAteData', v, { shouldValidate: true })}
                      erro={errors.inscricoesAteData?.message}
                    />
                  </div>
                  <div className={styles.campoHoraWrap}>
                    <span className={styles.subLabel}>Horário</span>
                    <input
                      type="text"
                      inputMode="numeric"
                      placeholder="hh:mm"
                      maxLength={5}
                      className={styles.inputData}
                      aria-label="Horário do prazo de inscrição"
                      {...register('inscricoesAteHora')}
                      onChange={(e) => setValue('inscricoesAteHora', formatarHoraDigitada(e.target.value), { shouldValidate: true })}
                    />
                    {errors.inscricoesAteHora && <span className={styles.erroCampo} data-campo-erro>{errors.inscricoesAteHora.message}</span>}
                  </div>
                </div>
                <span className={styles.campoHint}>
                  Vazio = aceita inscrição até o evento começar.
                </span>
              </div>

              <Revelar>
                {watch('inscricoesAteData') ? (
                  <div className={styles.grupoData}>
                    <span className={styles.labelData}>Depois do prazo, o participante pode cancelar?</span>
                    {tipoInscricao === 'PAGO' ? (
                      <div className={`${styles.segmentado} ${styles.segmentadoTriplo}`}>
                        {([
                          ['NAO_PERMITIDO', 'Não'],
                          ['PERMITIDO_COM_REEMBOLSO', 'Sim, com reembolso'],
                          ['PERMITIDO_SEM_REEMBOLSO', 'Sim, sem reembolso'],
                        ] as const).map(([valor, rotulo]) => (
                          <button
                            key={valor}
                            type="button"
                            className={`${styles.segmentoBtn} ${watch('politicaCancelamentoAposPrazo') === valor ? styles.segmentoAtivo : ''}`}
                            onClick={() => setValue('politicaCancelamentoAposPrazo', valor, { shouldValidate: true })}
                          >
                            {rotulo}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <label className={styles.toggleRow}>
                        <span className={styles.toggleTexto}>
                          <span className={styles.toggleTitulo}>Permitir cancelamento após o prazo</span>
                          <span className={styles.toggleDescricao}>Desmarque para travar a lista de inscritos no prazo.</span>
                        </span>
                        <span className={styles.switch}>
                          <input
                            type="checkbox"
                            className={styles.switchInput}
                            checked={watch('politicaCancelamentoAposPrazo') !== 'NAO_PERMITIDO'}
                            onChange={(e) => setValue('politicaCancelamentoAposPrazo',
                              e.target.checked ? 'PERMITIDO_COM_REEMBOLSO' : 'NAO_PERMITIDO', { shouldValidate: true })}
                          />
                          <span className={styles.switchTrilho} />
                        </span>
                      </label>
                    )}
                    <span className={styles.campoHint}>
                      Antes do prazo, cancelar é sempre livre e com reembolso.
                    </span>
                  </div>
                ) : null}
              </Revelar>

              <div className={styles.grupoData}>
                <span className={styles.labelData}>TIPO DE INSCRIÇÃO</span>
                <div className={styles.segmentado}>
                  <button
                    type="button"
                    className={`${styles.segmentoBtn} ${tipoInscricao === 'GRATUITO' ? styles.segmentoAtivo : ''}`}
                    onClick={() => {
                      setValue('tipoInscricao', 'GRATUITO', { shouldValidate: true })
                      setValue('preco', undefined, { shouldValidate: true })
                      if (watch('politicaCancelamentoAposPrazo') === 'PERMITIDO_SEM_REEMBOLSO') {
                        setValue('politicaCancelamentoAposPrazo', 'PERMITIDO_COM_REEMBOLSO', { shouldValidate: true })
                      }
                    }}
                  >
                    Gratuito
                  </button>
                  <button
                    type="button"
                    className={`${styles.segmentoBtn} ${tipoInscricao === 'PAGO' ? styles.segmentoAtivo : ''}`}
                    onClick={() => setValue('tipoInscricao', 'PAGO', { shouldValidate: true })}
                  >
                    Pago
                  </button>
                </div>
              </div>

              <label className={styles.toggleRow}>
                <span className={styles.toggleTexto}>
                  <span className={styles.toggleTitulo}>
                    <ClipboardCheck size={16} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
                    Check-in
                  </span>
                  <span className={styles.toggleDescricao}>
                    Marque quem chegou no dia e veja quantos vieram.
                  </span>
                </span>
                <span className={styles.switch}>
                  <input type="checkbox" className={styles.switchInput} {...register('controlaPresenca')} />
                  <span className={styles.switchTrilho} />
                </span>
              </label>

              {tipoInscricao === 'PAGO' && (
                <Revelar>
                  <Input
                    id="preco"
                    label="PREÇO"
                    placeholder="R$ 0,00"
                    inputMode="numeric"
                    error={errors.preco?.message}
                    value={formatarValorDigitado(preco)}
                    onChange={(e) => {
                      const digitos = e.target.value.replace(/\D/g, '')
                      const centavos = parseInt(digitos || '0', 10)
                      const emReais = (centavos / 100).toFixed(2)
                      setValue('preco', digitos === '' ? undefined : emReais, {
                        shouldValidate: true,
                        shouldDirty: true,
                      })
                    }}
                  />
                  <Transicao key={contaPagamento && !contaPagamento.conectada ? 'aviso' : 'hint'} modo="fade">
                  {contaPagamento && !contaPagamento.conectada ? (
                    <div className={styles.avisoContaPagamento} data-campo-erro>
                      {podeConectar ? (
                        <>
                          A igreja ainda não conectou uma conta pra receber pagamentos —
                          sem isso, ninguém consegue se inscrever neste evento.{' '}
                          <Link href="/configuracoes/igreja">Conectar agora</Link>
                        </>
                      ) : (
                        <>
                          A igreja ainda não conectou uma conta pra receber pagamentos —
                          sem isso, ninguém consegue se inscrever neste evento. Consulte o
                          administrador ou responsável pela igreja pra conectar.
                        </>
                      )}
                    </div>
                  ) : (
                    <span className={styles.campoHint}>
                      Cobrado na hora da inscrição.
                    </span>
                  )}
                  </Transicao>
                </Revelar>
              )}

              <BlocoRecolhivel
                id="campos-personalizados"
                titulo="Campos personalizados"
                descricao="Perguntas extras no formulário de inscrição"
                icone={<ClipboardCheck size={18} />}
              >
                {/* Sem eventoId (evento novo, ainda não salvo): o painel funciona só em
                    memória — `salvar()` é chamado depois, já com o id definitivo, por quem
                    registrou o callback (ver useEffect acima e useEventoForm.salvarEvento). */}
                <CamposPersonalizadosPainel ref={camposPersonalizadosRef} eventoId={eventoId} />
              </BlocoRecolhivel>
            </Revelar>
          )}
        </section>
      </div>

      <div className={styles.blocoFinal}>
        <PreviaEvento
          titulo={watch('titulo') as string}
          tipo={tipoAtual}
          inicioData={inicioData}
          inicioHora={watch('inicioHora') as string}
          fimData={fimData}
          localResumo={localResumo}
          fotoId={fotoIdAtual}
          requerInscricao={!!requerInscricao}
          tipoInscricao={(tipoInscricao as 'GRATUITO' | 'PAGO') ?? 'GRATUITO'}
          preco={preco}
          vagas={vagasAtual}
          inscricoesAteData={watch('inscricoesAteData') as string}
          exclusivoMembros={!!exclusivoMembros}
          idadeMin={idadeMinAtual}
          idadeMax={idadeMaxAtual}
          restricaoEstadoCivil={restricaoEstadoCivilAtual}
          restricaoSexo={restricaoSexoAtual}
          restritoPropriaIgreja={!!watch('restritoPropriaIgreja')}
          temFamilia={temFamilia}
          rotuloOutrasCongregacoes={rotuloOutrasCongregacoes}
          controlaPresenca={!!watch('controlaPresenca')}
          camposFaltando={camposFaltando}
        />

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}
        {erroValidacao && <div className={styles.erroGeral}>{erroValidacao}</div>}

        <div className={styles.acoes}>
          <Button
            type="submit"
            variant="primary"
            size="lg"
            isLoading={isLoading || isVerificandoImpacto}
            disabled={isLoading || isVerificandoImpacto}
            style={{ width: '100%' }}
          >
            {ehEdicao ? 'Salvar alterações' : 'Salvar evento'}
          </Button>
          <button type="button" onClick={() => router.back()} className={styles.cancelarLink}>Cancelar</button>
        </div>
      </div>

      {impactoAfetados && impactoAfetados.length > 0 && (
        <ModalImpactoRestricao
          afetados={impactoAfetados}
          isLoading={isLoading}
          onManterTodos={() => onConfirmarImpacto(false)}
          onCancelarNaoElegiveis={() => onConfirmarImpacto(true)}
          onClose={onFecharImpacto}
        />
      )}

      {impactoMudancaPreco && impactoMudancaPreco.tipo !== 'SEM_IMPACTO' && (
        <ModalImpactoMudancaPreco
          impacto={impactoMudancaPreco}
          isLoading={isLoading}
          onConfirmar={onConfirmarMudancaPreco}
          onClose={onFecharMudancaPreco}
        />
      )}

      {aguardandoEscopoEdicao && (
        <ModalEscopoEdicaoEvento
          titulo={(watch('titulo') as string) ?? ''}
          onEscolher={onEscolherEscopoEdicao}
          onClose={onFecharEscopoEdicao}
        />
      )}

      <OverlayCarregando ativo={isLoading} texto={ehEdicao ? 'Salvando alterações…' : 'Salvando…'} />
    </form>
  )
}
