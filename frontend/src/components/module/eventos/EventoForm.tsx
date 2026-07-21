'use client'

import Link from 'next/link'
import { CalendarClock, FileText, MapPin, ImageIcon, Info, Ticket, AlertTriangle } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './EventoForm.module.css'
import type { UseFormReturn } from 'react-hook-form'
import type { EventoFormInput, EventoFormData } from '@/lib/validators'

type EventoFormProps = UseFormReturn<EventoFormInput, unknown, EventoFormData> & {
  isFormIncomplete: boolean
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  onSubmit: (data: EventoFormData) => void
}

export function EventoForm(props: EventoFormProps) {
  const {
    register, handleSubmit, watch, setValue,
    formState: { errors },
    erroGeral, isLoading, isFormIncomplete, onSubmit, ehEdicao,
  } = props

  const requerInscricao = watch('requerInscricao')
  const tipoInscricao = watch('tipoInscricao')
  const exclusivoBatizados = watch('exclusivoBatizados')

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
            <Input
              id="local"
              label="LOCAL DO EVENTO"
              placeholder="Ex: Auditório Principal - Sede"
              error={errors.local?.message}
              {...register('local')}
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
                    <input
                      type="date"
                      className={styles.inputData}
                      {...register('inicioData')}
                    />
                    {errors.inicioData && <span className={styles.erroCampo}>{errors.inicioData.message}</span>}
                  </div>
                  <div className={styles.campoHoraWrap}>
                    <input
                      type="time"
                      className={styles.inputData}
                      {...register('inicioHora')}
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
                    <input
                      type="date"
                      className={styles.inputData}
                      {...register('fimData')}
                    />
                    {errors.fimData && <span className={styles.erroCampo}>{errors.fimData.message}</span>}
                  </div>
                  <div className={styles.campoHoraWrap}>
                    <input
                      type="time"
                      className={styles.inputData}
                      {...register('fimHora')}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Área de imagem — desabilitada (sem storage ainda) */}
            <div className={styles.imagemWrap}>
              <span className={styles.labelData}>IMAGEM DO EVENTO</span>
              <div className={styles.imagemUpload}>
                <ImageIcon size={24} />
                <span>Adicionar imagem</span>
                <small>Em breve</small>
              </div>
            </div>

            <div className={styles.infoBox}>
              <Info size={18} className={styles.infoIcon} />
              <p className={styles.infoText}>
                O evento aparecerá na agenda da igreja assim que for salvo.
              </p>
            </div>
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
                      type="number"
                      label="PREÇO"
                      placeholder="0,00"
                      min={0.01}
                      step={0.01}
                      leftIcon={<span>R$</span>}
                      error={errors.preco?.message}
                      {...register('preco')}
                    />
                    <span className={styles.campoHint}>
                      Informativo. O pagamento é combinado com a igreja — informe o PIX ou um
                      contato na descrição do evento.
                    </span>
                  </div>
                )}

                <label className={styles.toggleRow}>
                  <span className={styles.toggleTexto}>
                    <span className={styles.toggleTitulo}>Somente membros da igreja</span>
                  </span>
                  <span className={styles.switch}>
                    <input type="checkbox" className={styles.switchInput} {...register('exclusivoMembros')} />
                    <span className={styles.switchTrilho} />
                  </span>
                </label>

                <label className={styles.toggleRow}>
                  <span className={styles.toggleTexto}>
                    <span className={styles.toggleTitulo}>Somente membros batizados</span>
                  </span>
                  <span className={styles.switch}>
                    <input type="checkbox" className={styles.switchInput} {...register('exclusivoBatizados')} />
                    <span className={styles.switchTrilho} />
                  </span>
                </label>

                {exclusivoBatizados && (
                  <div className={styles.infoBox}>
                    <AlertTriangle size={18} className={styles.infoIcon} />
                    <p className={styles.infoText}>
                      Membros que não estiverem marcados como batizados não poderão se
                      inscrever nem ser inscritos.
                    </p>
                  </div>
                )}
              </div>
            )}
          </section>

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

          <div className={styles.acoes}>
            <Button
              type="submit"
              variant="primary"
              size="lg"
              isLoading={isLoading}
              disabled={isFormIncomplete || isLoading}
              style={{ width: '100%' }}
            >
              {ehEdicao ? 'Salvar alterações' : 'Salvar evento'}
            </Button>
            <Link href="/eventos" className={styles.cancelarLink}>Cancelar</Link>
          </div>
        </div>
      </div>
    </form>
  )
}