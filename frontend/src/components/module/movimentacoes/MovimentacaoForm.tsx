'use client'

import Link from 'next/link'
import { Wallet, ArrowDownCircle, ArrowUpCircle, Info } from 'lucide-react'
import { useCategoriasSelect } from '@/hooks/financeiro/categoria/useCategoriaSelect'
import { SelecaoPessoa } from './SelecaoPessoa'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { formatarMoeda, formatarValorDigitado } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { UseFormReturn } from 'react-hook-form'
import type { MovimentacaoFormInput, MovimentacaoFormData } from '@/lib/validators'
import type { CategoriaResponse, TipoCategoria } from '@/types/financeiro/categoria.type'
import type { TipoMovimentacao } from '@/types/financeiro/movimentacao.type'
import styles from './MovimentacaoForm.module.css'

type MovimentacaoFormProps = UseFormReturn<MovimentacaoFormInput, unknown, MovimentacaoFormData> & {
  isFormIncomplete: boolean
  erroGeral: string | null
  isLoading: boolean
  ehEdicao: boolean
  onSubmit: (data: MovimentacaoFormData) => void
  // Nome vem assim da API (`MovimentacaoResponse.pessoaNome`, backend) — não renomeado por lá.
  pessoaNomeInicial?: string
}

export function MovimentacaoForm(props: MovimentacaoFormProps) {
  const {
    register, handleSubmit, watch, setValue,
    formState: { errors },
    erroGeral, isLoading, isFormIncomplete, ehEdicao, onSubmit, pessoaNomeInicial,
  } = props

  const tipo = watch('tipo') as TipoMovimentacao | undefined
  const categoriaId = watch('categoriaId') as string
  const valor = watch('valor') as string
  const pessoaId = watch('pessoaId') as string | undefined
  const dataMovimentacao = (watch('dataMovimentacao') as string) ?? ''

  const { data: categorias, isPending: categoriasCarregando } = useCategoriasSelect()

  // Nenhuma categoria cadastrada na igreja (após carregar): sem isso não dá pra lançar nada.
  const semNenhumaCategoria = !categoriasCarregando && (categorias?.length ?? 0) === 0

  const categoriasCompativeis = (categorias ?? []).filter((c: CategoriaResponse) => {
    if (!tipo) return true
    if (c.tipo === 'AMBOS') return true
    return c.tipo === (tipo as unknown as TipoCategoria)
  })

  // Existe categoria, mas nenhuma compatível com o tipo escolhido.
  const semCategoriaParaTipo = !semNenhumaCategoria && !!tipo && categoriasCompativeis.length === 0

  const labelPessoa = tipo === 'SAIDA' ? 'Beneficiário' : 'Contribuinte'
  const categoriaNome = categoriasCompativeis.find((c: CategoriaResponse) => c.id === categoriaId)?.nome

  const valorInvalido = !valor || parseFloat(valor) <= 0

  return (
    <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
      <div className={styles.colunas}>
        {/* Coluna esquerda — campos */}
        <div className={styles.colunaEsquerda}>
          <section className={styles.secao}>
            <div className={styles.secaoHeader}>
              <span className={styles.secaoIcone}><Wallet size={20} /></span>
              <h2 className={styles.secaoTitulo}>Dados da movimentação</h2>
            </div>

            <div className={styles.campos}>
              {/* Tipo */}
              <div className={styles.campo}>
                <span className={styles.label}>TIPO DE MOVIMENTAÇÃO</span>
                <div className={styles.tipoSelecao}>
                  <button
                    type="button"
                    className={`${styles.tipoBtn} ${tipo === 'ENTRADA' ? styles.tipoEntradaAtivo : ''}`}
                    onClick={() => {
                      setValue('tipo', 'ENTRADA', { shouldValidate: true, shouldDirty: true })
                      setValue('categoriaId', '')
                    }}
                  >
                    <ArrowDownCircle size={22} />
                    <span className={styles.tipoLabel}>Entrada</span>
                    <span className={styles.tipoDesc}>Dízimos, ofertas, doações</span>
                  </button>
                  <button
                    type="button"
                    className={`${styles.tipoBtn} ${tipo === 'SAIDA' ? styles.tipoSaidaAtivo : ''}`}
                    onClick={() => {
                      setValue('tipo', 'SAIDA', { shouldValidate: true, shouldDirty: true })
                      setValue('categoriaId', '')
                    }}
                  >
                    <ArrowUpCircle size={22} />
                    <span className={styles.tipoLabel}>Saída</span>
                    <span className={styles.tipoDesc}>Despesas, contas, salários</span>
                  </button>
                </div>
                {errors.tipo && <span className={styles.erroCampo}>{errors.tipo.message}</span>}
              </div>

              {/* Valor */}
              <div className={styles.campo}>
                <label className={styles.label} htmlFor="valor">VALOR</label>
                <input
                    id="valor"
                    className={styles.inputValor}
                    placeholder="R$ 0,00"
                    value={formatarValorDigitado(valor)}
                    onChange={(e) => {
                    const digitos = e.target.value.replace(/\D/g, '')
                    const centavos = parseInt(digitos || '0', 10)
                    const emReais = (centavos / 100).toFixed(2)  
                    setValue('valor', digitos === '' ? '' : emReais, { shouldValidate: true, shouldDirty: true })
                    }}
                    inputMode="numeric"
                />
                {errors.valor && <span className={styles.erroCampo}>{errors.valor.message}</span>}
              </div>

              <div className={styles.linha2}>
                <div className={styles.campo}>
                  <label className={styles.label} htmlFor="categoriaId">CATEGORIA</label>
                  {semNenhumaCategoria ? (
                    <div className={styles.avisoCategoria}>
                      <p className={styles.avisoCategoriaTexto}>
                        Nenhuma categoria cadastrada. Para lançar entradas e saídas, crie uma categoria antes.
                      </p>
                      <Link href="/financeiro/categorias" className={styles.avisoCategoriaLink}>
                        Criar categoria
                      </Link>
                    </div>
                  ) : (
                    <>
                      <select
                        id="categoriaId"
                        className={styles.select}
                        {...register('categoriaId')}
                        disabled={!tipo}
                      >
                        <option value="">{tipo ? 'Selecione' : 'Escolha o tipo primeiro'}</option>
                        {categoriasCompativeis.map((c: CategoriaResponse) => (
                          <option key={c.id} value={c.id}>{c.nome}</option>
                        ))}
                      </select>
                      {semCategoriaParaTipo && (
                        <span className={styles.avisoCategoriaTipo}>
                          Nenhuma categoria de {tipo === 'ENTRADA' ? 'entrada' : 'saída'}.{' '}
                          <Link href="/financeiro/categorias" className={styles.avisoCategoriaLink}>
                            Criar uma
                          </Link>
                        </span>
                      )}
                      {errors.categoriaId && <span className={styles.erroCampo}>{errors.categoriaId.message}</span>}
                    </>
                  )}
                </div>

                <div className={styles.campo}>
                  <label className={styles.label} htmlFor="dataMovimentacao">DATA</label>
                  <CampoData
                    id="dataMovimentacao"
                    semLabel
                    value={dataMovimentacao}
                    onChange={(v) => setValue('dataMovimentacao', v, { shouldValidate: true, shouldDirty: true })}
                  />
                  {errors.dataMovimentacao && <span className={styles.erroCampo}>{errors.dataMovimentacao.message}</span>}
                </div>
              </div>

              {/* Pessoa (contribuinte/beneficiário) */}
              <div className={styles.campo}>
                <label className={styles.label}>
                  {labelPessoa} <span className={styles.opcional}>(opcional)</span>
                </label>
                <SelecaoPessoa
                  pessoaIdSelecionado={pessoaId || undefined}
                  nomeSelecionado={pessoaId ? pessoaNomeInicial : undefined}
                  label={labelPessoa}
                  onSelecionar={(id) => setValue('pessoaId', id ?? '', { shouldDirty: true })}
                />
              </div>

              {/* Descrição */}
              <div className={styles.campo}>
                <label className={styles.label} htmlFor="descricao">
                  DESCRIÇÃO <span className={styles.opcional}>(opcional)</span>
                </label>
                <textarea
                  id="descricao"
                  className={styles.textarea}
                  placeholder="Detalhes da movimentação..."
                  {...register('descricao')}
                />
              </div>
            </div>
          </section>
        </div>

        {/* Coluna direita — resumo */}
        <div className={styles.colunaDireita}>
          <div className={styles.resumo}>
            <h3 className={styles.resumoTitulo}>Resumo da operação</h3>

            <div className={styles.resumoLinha}>
              <span className={styles.resumoLabel}>Tipo</span>
              <span className={styles.resumoValor}>
                {tipo ? (tipo === 'ENTRADA' ? 'Entrada' : 'Saída') : '—'}
              </span>
            </div>
            <div className={styles.resumoLinha}>
              <span className={styles.resumoLabel}>Categoria</span>
              <span className={styles.resumoValor}>{categoriaNome ?? '—'}</span>
            </div>

            <div className={styles.resumoTotal}>
              <span className={styles.resumoTotalLabel}>Valor</span>
              <span className={`${styles.resumoTotalValor} ${tipo === 'SAIDA' ? styles.saida : styles.entrada}`}>
                {valor ? formatarMoeda(valor) : 'R$ 0,00'}
              </span>
            </div>

            <div className={styles.infoBox}>
              <Info size={18} className={styles.infoIcon} />
              <p className={styles.infoText}>
                Ao salvar, esta movimentação será refletida nos relatórios do período.
              </p>
            </div>
          </div>

          {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

          <div className={styles.acoes}>
            <button type="submit" className={styles.btnSalvar} disabled={isFormIncomplete || valorInvalido || isLoading || semNenhumaCategoria}>
              {isLoading ? 'Salvando…' : ehEdicao ? 'Salvar alterações' : 'Salvar movimentação'}
            </button>
            <Link href="/financeiro/movimentacoes" className={styles.cancelarLink}>Cancelar</Link>
          </div>
        </div>
      </div>
    </form>
  )
}