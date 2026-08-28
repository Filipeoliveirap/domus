'use client'

import { useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { CalendarClock, FileText, MapPin, Info, Ticket, UserCog, ClipboardCheck, Users, Building2, Repeat } from 'lucide-react'
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
import styles from './EventoForm.module.css'
import type { UseFormReturn } from 'react-hook-form'
import type { EventoFormInput, EventoFormData } from '@/lib/validators'
import type { InscritoImpactado, ImpactoMudancaPrecoResponse, RestricaoEstadoCivil, RestricaoSexo, EscopoEdicaoEvento } from '@/types/evento.type'

type EventoFormProps = UseFormReturn<EventoFormInput, unknown, EventoFormData> & {
  isFormIncomplete: boolean
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  eventoId?: string
  responsavelNomeInicial?: string
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
    register, handleSubmit, watch, setValue,
    formState: { errors },
    erroGeral, isLoading, isFormIncomplete, onSubmit, ehEdicao, eventoId, responsavelNomeInicial,
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
  const tipoAtual = (watch('tipo') as string) ?? ''
  const responsavelAtual = watch('responsavelPessoaId') as string | undefined
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

  // Um botão só ("Salvar alterações") salva evento + campos personalizados — sem isso,
  // existiam dois botões de salvar na mesma tela, confuso qual fazia o quê. O painel pode
  // existir ANTES do evento ter id (evento novo) — por isso quem chama `salvar()` de
  // verdade é o próprio useEventoForm (registrado aqui), depois de criar/atualizar o
  // evento e já sabendo o id definitivo.
  const camposPersonalizadosRef = useRef<CamposPersonalizadosHandle>(null)

  useEffect(() => {
    registrarSalvarCamposPersonalizados((eventoIdSalvo) => (
      camposPersonalizadosRef.current?.salvar(eventoIdSalvo) ?? Promise.resolve()
    ))
    return () => registrarSalvarCamposPersonalizados(null)
  }, [registrarSalvarCamposPersonalizados])

  return (
    <form className={styles.form} onSubmit={(e) => handleSubmit(onSubmit)(e)}>
      <div className={styles.colunas}>
        {/* ─── Coluna esquerda ─── */}
        <div className={styles.colunaEsquerda}>
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><FileText size={20} /></span>
              <h2 className={styles.secaoTitulo}>Informações do evento</h2>
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
            </div>
          </section>

          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><MapPin size={20} /></span>
              <h2 className={styles.secaoTitulo}>Local</h2>
            </div>
            <SeletorLocal
              localId={localIdAtual}
              localTexto={localTextoAtual}
              error={errors.localId?.message ?? errors.localTexto?.message}
              onChangeLocalId={(id) => {
                setValue('localId', id, { shouldDirty: true })
                if (id) setValue('localTexto', undefined, { shouldDirty: true })
              }}
              onChangeLocalTexto={(texto) => {
                setValue('localTexto', texto, { shouldDirty: true })
                if (texto) setValue('localId', undefined, { shouldDirty: true })
              }}
              onCapacidadeSugerida={(cap) => {
                if (requerInscricao && vagasAtual == null) {
                  setValue('vagas', cap, { shouldDirty: true, shouldValidate: true })
                }
              }}
            />
          </section>
        </div>

        {/* ─── Coluna direita ─── */}
        <div className={styles.colunaDireita}>
          <section className={styles.secaoData}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><CalendarClock size={20} /></span>
              <h2 className={styles.secaoTitulo}>Data e horário</h2>
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
                    {errors.inicioHora && <span className={styles.erroCampo}>{errors.inicioHora.message}</span>}
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
                    {errors.fimHora && <span className={styles.erroCampo}>{errors.fimHora.message}</span>}
                  </div>
                </div>
              </div>
            </div>

            {/* Repetição — só faz sentido no cadastro; editar uma ocorrência existente usa o
                seletor de escopo (só esta/esta e as seguintes/toda a série), não este toggle. */}
            {!ehEdicao && (
              <div className={styles.campos}>
                <label className={styles.toggleRow}>
                  <span className={styles.toggleTexto}>
                    <span className={styles.toggleTitulo}>
                      <Repeat size={16} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
                      Repetir
                    </span>
                    <span className={styles.toggleDescricao}>
                      Cadastre uma vez e as próximas ocorrências aparecem sozinhas.
                    </span>
                  </span>
                  <span className={styles.switch}>
                    <input type="checkbox" className={styles.switchInput} {...register('repetir')} />
                    <span className={styles.switchTrilho} />
                  </span>
                </label>

                {repetir && (
                  <>
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
                      <div>
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
                          <span className={styles.erroCampo}>{errors.recorrenciaDiasSemana.message}</span>
                        )}
                      </div>
                    )}

                    {recorrenciaFrequencia === 'MENSAL' && (
                      <Select id="recorrencia-tipo-mensal" label="REPETE" placeholder="Selecione"
                        options={[
                          { value: 'DIA_FIXO', label: 'No mesmo dia do mês' },
                          { value: 'DIA_DA_SEMANA', label: 'Na mesma posição (ex.: toda 1ª terça)' },
                        ]}
                        error={errors.recorrenciaTipoMensal?.message}
                        {...register('recorrenciaTipoMensal')} />
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
                      <CampoData
                        id="recorrencia-data-fim"
                        label="Data final"
                        value={(watch('recorrenciaDataFim') as string) ?? ''}
                        onChange={(v) => setValue('recorrenciaDataFim', v, { shouldValidate: true })}
                        erro={errors.recorrenciaDataFim?.message}
                      />
                    )}

                    {recorrenciaFimTipo === 'CONTAGEM' && (
                      <Input id="recorrencia-numero-ocorrencias" label="NÚMERO DE OCORRÊNCIAS" type="number" min={1}
                        placeholder="Ex.: 10"
                        error={errors.recorrenciaNumeroOcorrencias?.message}
                        {...register('recorrenciaNumeroOcorrencias')} />
                    )}
                  </>
                )}
              </div>
            )}

            {/* Imagem do evento (capa/banner) */}
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

            <div className={styles.infoBox}>
              <Info size={18} className={styles.infoIcon} />
              <p className={styles.infoText}>
                O evento aparecerá na agenda da igreja assim que for salvo.
              </p>
            </div>
          </section>

          {/* ─── Organização ─── */}
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><UserCog size={20} /></span>
              <h2 className={styles.secaoTitulo}>Organização</h2>
            </div>
            <SeletorResponsavel
              valor={responsavelAtual}
              nomeInicial={responsavelNomeInicial}
              onChange={(id) => setValue('responsavelPessoaId', id, { shouldDirty: true })}
            />

            {temFamilia && (
              <label className={styles.toggleRow}>
                <span className={styles.toggleTexto}>
                  <span className={styles.toggleTitulo}>
                    <Building2 size={16} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
                    Apenas minha igreja
                  </span>
                  <span className={styles.toggleDescricao}>
                    Ative para este evento não aparecer para {concordar(congregacao.genero, 'os_min')} demais {congregacao.plural.toLowerCase()}.
                  </span>
                </span>
                <span className={styles.switch}>
                  <input type="checkbox" className={styles.switchInput} {...register('restritoPropriaIgreja')} />
                  <span className={styles.switchTrilho} />
                </span>
              </label>
            )}
          </section>

          {/* ─── Para quem é ─── */}
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><Users size={20} /></span>
              <h2 className={styles.secaoTitulo}>Para quem é</h2>
            </div>
            <BlocoParaQuemE
              recorteEtario={recorteEtarioAtual}
              idadeMin={idadeMinAtual}
              idadeMax={idadeMaxAtual}
              restricaoEstadoCivil={restricaoEstadoCivilAtual}
              restricaoSexo={restricaoSexoAtual}
              exclusivoMembros={!!exclusivoMembros}
              mostrarExclusivoMembros={requerInscricao}
              erroIdadeMax={errors.idadeMax?.message}
              onChangeRecorteEtario={(v) => setValue('recorteEtario', v, { shouldDirty: true })}
              onChangeIdadeMin={(v) => setValue('idadeMin', v, { shouldDirty: true, shouldValidate: true })}
              onChangeIdadeMax={(v) => setValue('idadeMax', v, { shouldDirty: true, shouldValidate: true })}
              onChangeEstadoCivil={(v) => setValue('restricaoEstadoCivil', v, { shouldDirty: true })}
              onChangeSexo={(v) => setValue('restricaoSexo', v, { shouldDirty: true })}
              onChangeExclusivoMembros={(v) => setValue('exclusivoMembros', v, { shouldDirty: true })}
            />
          </section>

          {/* ─── Inscrições ─── */}
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><Ticket size={20} /></span>
              <h2 className={styles.secaoTitulo}>Inscrições</h2>
            </div>

            <label className={styles.toggleRow}>
              <span className={styles.toggleTexto}>
                <span className={styles.toggleTitulo}>Requer inscrição prévia</span>
                <span className={styles.toggleDescricao}>
                  Ative para controlar vagas, preço e restrições de quem pode participar.
                </span>
              </span>
              <span className={styles.switch}>
                <input type="checkbox" className={styles.switchInput} {...register('requerInscricao')} />
                <span className={styles.switchTrilho} />
              </span>
            </label>

            {requerInscricao && (
              <div className={styles.campos}>
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
                  <span className={styles.campoHint}>Deixe vazio para não limitar.</span>
                </div>

                <div className={styles.grupoData}>
                  <span className={styles.labelData}>TIPO DE INSCRIÇÃO</span>
                  <div className={styles.segmentado}>
                    <button
                      type="button"
                      className={`${styles.segmentoBtn} ${tipoInscricao === 'GRATUITO' ? styles.segmentoAtivo : ''}`}
                      onClick={() => {
                        setValue('tipoInscricao', 'GRATUITO', { shouldValidate: true })
                        setValue('preco', undefined, { shouldValidate: true })
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
                      Controlar presença
                    </span>
                    <span className={styles.toggleDescricao}>
                      Ative para marcar quem realmente compareceu e ver o relatório de presença deste evento.
                    </span>
                  </span>
                  <span className={styles.switch}>
                    <input type="checkbox" className={styles.switchInput} {...register('controlaPresenca')} />
                    <span className={styles.switchTrilho} />
                  </span>
                </label>

                {tipoInscricao === 'PAGO' && (
                  <div>
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
                    {contaPagamento && !contaPagamento.conectada ? (
                      <div className={styles.avisoContaPagamento}>
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
                        Cobrado automaticamente na inscrição, através da conta de recebimento
                        conectada pela igreja.
                      </span>
                    )}
                  </div>
                )}

              </div>
            )}
          </section>
        </div>
      </div>

      {/* Fora do grid de 2 colunas de propósito: o editor + prévia lado a lado de campos
          personalizados fica espremido preso na largura de meia página — aqui usa a largura
          inteira do formulário. */}
      <div className={styles.blocoFinal}>
        {requerInscricao && (
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><ClipboardCheck size={20} /></span>
              <h2 className={styles.secaoTitulo}>Campos personalizados</h2>
            </div>
            {/* Sem eventoId (evento novo, ainda não salvo): o painel funciona só em
                memória — `salvar()` é chamado depois, já com o id definitivo, por quem
                registrou o callback (ver useEffect acima e useEventoForm.salvarEvento). */}
            <CamposPersonalizadosPainel ref={camposPersonalizadosRef} eventoId={eventoId} />
          </section>
        )}

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

        <div className={styles.acoes}>
          <Button
            type="submit"
            variant="primary"
            size="lg"
            isLoading={isLoading || isVerificandoImpacto}
            disabled={isFormIncomplete || isLoading || isVerificandoImpacto || precisaConectarContaPagamento}
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