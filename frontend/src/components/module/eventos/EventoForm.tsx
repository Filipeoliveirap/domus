'use client'

import Link from 'next/link'
import { CalendarClock, FileText, MapPin, Info, Ticket, UserCog } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
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
import { useTiposEvento } from '@/hooks/evento/useTiposEvento'
import styles from './EventoForm.module.css'
import type { UseFormReturn } from 'react-hook-form'
import type { EventoFormInput, EventoFormData } from '@/lib/validators'
import type { InscritoImpactado, RestricaoEstadoCivil, RestricaoSexo } from '@/types/evento.type'

type EventoFormProps = UseFormReturn<EventoFormInput, unknown, EventoFormData> & {
  isFormIncomplete: boolean
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  responsavelNomeInicial?: string
  onSubmit: (data: EventoFormData) => void
  // ─── Impacto retroativo (Task 9) ───
  impactoAfetados: InscritoImpactado[] | null
  isVerificandoImpacto: boolean
  onConfirmarImpacto: (cancelarNaoElegiveis: boolean) => void
  onFecharImpacto: () => void
}

export function EventoForm(props: EventoFormProps) {
  const {
    register, handleSubmit, watch, setValue,
    formState: { errors },
    erroGeral, isLoading, isFormIncomplete, onSubmit, ehEdicao, responsavelNomeInicial,
    impactoAfetados, isVerificandoImpacto, onConfirmarImpacto, onFecharImpacto,
  } = props

  const requerInscricao = watch('requerInscricao')
  const tipoInscricao = watch('tipoInscricao')
  const exclusivoMembros = watch('exclusivoMembros')
  const inicioData = (watch('inicioData') as string) ?? ''
  const fimData = (watch('fimData') as string) ?? ''
  const preco = (watch('preco') as string) ?? ''
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

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
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

              {/*
                Tipo: texto livre com sugestões (chips). As sugestões vêm do servidor já
                ordenadas — o que a igreja mais usa primeiro, sementes depois — e o campo
                aceita qualquer valor digitado. NÃO é "categoria" (esse nome é do financeiro).
              */}
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
            {/*
              O SeletorLocal alterna entre local cadastrado (localId) e texto livre
              (localTexto), mantendo os dois mutuamente exclusivos. A capacidade do local
              escolhido SUGERE vagas — mas só quando o campo está vazio, nunca sobrescrevendo
              o que a pessoa já digitou (cabem 300, mas o ônibus pode limitar em 80).
            */}
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
                // Sugere vagas SÓ se o evento já pede inscrição E o campo está vazio. NÃO
                // liga requerInscricao: esse é o interruptor mestre do domínio ("evento que
                // se organiza" vs. "evento que só acontece"), e escolher um local só para
                // registrar ONDE o evento é não pode convertê-lo em evento com inscrição.
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
              {/*
                F4: `<input type="date">`/`type="time"` renderizam no idioma do NAVEGADOR/SO,
                não no da página — em um Chrome configurado em en-US o campo aparecia em
                mm/dd/aaaa mesmo com o resto da tela em pt-BR (bug relatado ao vivo). Trocado
                por texto mascarado (dd/mm/aaaa e hh:mm, sempre 24h) que formata a cada tecla —
                o mesmo padrão já usado para telefone/preço neste form. Continua 100% teclado,
                com `inputMode="numeric"` para abrir o teclado numérico no celular.
              */}
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

            {/* Imagem do evento (capa/banner) */}
            <div className={styles.imagemWrap}>
              <span className={styles.labelData}>IMAGEM DO EVENTO</span>
              <UploadFoto
                valor={fotoIdAtual}
                onChange={(id) => setValue('fotoId', id, { shouldValidate: true, shouldDirty: true })}
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
                    <span className={styles.campoHint}>
                      Informativo. O pagamento é combinado com a igreja — informe o PIX ou um
                      contato na descrição do evento.
                    </span>
                  </div>
                )}

                {/*
                  "Para quem é" (elegibilidade): recolhido em "Todos" por padrão. Só faz
                  sentido dentro de "requer inscrição" — não há como restringir quem se
                  inscreve num evento que não tem inscrição.
                */}
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
              </div>
            )}
          </section>

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

          <div className={styles.acoes}>
            <Button
              type="submit"
              variant="primary"
              size="lg"
              isLoading={isLoading || isVerificandoImpacto}
              disabled={isFormIncomplete || isLoading || isVerificandoImpacto}
              style={{ width: '100%' }}
            >
              {ehEdicao ? 'Salvar alterações' : 'Salvar evento'}
            </Button>
            <Link href="/eventos" className={styles.cancelarLink}>Cancelar</Link>
          </div>
        </div>
      </div>

      {/*
        Só aparece quando o backend devolveu gente afetada (POST /impacto-restricao) —
        evento novo nunca tem inscritos, então este passo nem entra na jogada.
      */}
      {impactoAfetados && impactoAfetados.length > 0 && (
        <ModalImpactoRestricao
          afetados={impactoAfetados}
          isLoading={isLoading}
          onManterTodos={() => onConfirmarImpacto(false)}
          onCancelarNaoElegiveis={() => onConfirmarImpacto(true)}
          onClose={onFecharImpacto}
        />
      )}
    </form>
  )
}