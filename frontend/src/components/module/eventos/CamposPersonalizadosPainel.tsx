'use client'

import { forwardRef, useImperativeHandle, useState } from 'react'
import { Plus, Trash2, GripVertical, RotateCcw } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useSalvarCamposPersonalizados } from '@/hooks/evento/useSalvarCamposPersonalizados'
import type { CampoPersonalizadoRequest, CampoPersonalizadoResponse, TipoCampoPersonalizado } from '@/types/campoPersonalizado.type'
import styles from './CamposPersonalizadosPainel.module.css'

const TIPOS: { value: TipoCampoPersonalizado; label: string; ajuda: string }[] = [
  { value: 'TEXTO_CURTO', label: 'Texto livre', ajuda: 'A pessoa escreve a resposta com as próprias palavras.' },
  { value: 'OPCAO_UNICA', label: 'Lista — escolher 1', ajuda: 'Você define as opções; a pessoa escolhe só uma.' },
  { value: 'MULTIPLA_ESCOLHA', label: 'Lista — marcar várias', ajuda: 'Você define as opções; a pessoa pode marcar mais de uma.' },
  { value: 'SIM_NAO', label: 'Sim ou não', ajuda: 'A pessoa só marca uma caixinha: sim ou não.' },
]

function ajudaDoTipo(tipo: TipoCampoPersonalizado): string {
  return TIPOS.find((t) => t.value === tipo)?.ajuda ?? ''
}

function limparOpcoes(opcoes: string[] | null): string[] {
  return (opcoes ?? []).map((o) => o.trim()).filter(Boolean)
}

function campoVazio(ordem: number): CampoPersonalizadoRequest {
  return { id: null, label: '', placeholder: null, tipo: 'TEXTO_CURTO', opcoes: null, obrigatorio: false, visivelAoPublico: true, ordem, mapeamento: null }
}

function paraRequest(c: CampoPersonalizadoResponse): CampoPersonalizadoRequest {
  return {
    id: c.id, label: c.label, placeholder: c.placeholder, tipo: c.tipo,
    opcoes: c.opcoes.length ? c.opcoes : null, obrigatorio: c.obrigatorio,
    visivelAoPublico: c.visivelAoPublico, ordem: c.ordem, mapeamento: c.mapeamento,
  }
}

export interface CamposPersonalizadosHandle {
  /** @param eventoId sobrescreve o id usado pra salvar — necessário quando o painel é
   *  montado ANTES do evento existir (evento novo): quem chama só sabe o id definitivo
   *  depois de criar o evento, então passa aqui na hora. Omitido = usa o id do próprio
   *  painel (evento já existente, edição). */
  salvar: (eventoId?: string) => Promise<void>
}

export const CamposPersonalizadosPainel = forwardRef<CamposPersonalizadosHandle, { eventoId?: string }>(
  function CamposPersonalizadosPainel({ eventoId }, ref) {
    // Evento novo (sem id ainda): nada pra buscar — começa vazio, e o painel funciona
    // inteiramente em memória até `salvar(idDefinitivo)` ser chamado depois de criado.
    const { data, isLoading } = useCamposPersonalizados(eventoId ?? '')

    if (eventoId && (isLoading || !data)) return null
    return <PainelEditor key={eventoId ?? 'novo'} ref={ref} eventoId={eventoId} dadosIniciais={eventoId ? data! : []} />
  },
)

const PainelEditor = forwardRef<
  CamposPersonalizadosHandle,
  { eventoId?: string; dadosIniciais: CampoPersonalizadoResponse[] }
>(function PainelEditor({ eventoId, dadosIniciais }, ref) {
  const { salvar } = useSalvarCamposPersonalizados()
  const [campos, setCampos] = useState<CampoPersonalizadoRequest[]>(() => dadosIniciais.map(paraRequest))

  useImperativeHandle(ref, () => ({
    salvar: async (eventoIdParam?: string) => {
      const idParaSalvar = eventoIdParam ?? eventoId
      if (!idParaSalvar) return
      const camposLimpos = campos.map((c) => ({ ...c, opcoes: c.opcoes ? limparOpcoes(c.opcoes) : null }))
      await salvar(idParaSalvar, camposLimpos)
    },
  }), [campos, eventoId, salvar])

  // O mapeamento não depende mais de tipo/opções: o valor auto-preenchido/pulado vem sempre
  // do cadastro da Pessoa (pessoa.estadoCivil, pessoa.sexo…), nunca do texto das opções do
  // campo — então mudar tipo ou reescrever as opções não invalida o "pula pergunta pra quem
  // já tem esse dado". A única forma de tirar o mapeamento é escolher outro no seletor.
  function atualizarCampo(indice: number, patch: Partial<CampoPersonalizadoRequest>) {
    setCampos((atual) => atual.map((c, i) => (i === indice ? { ...c, ...patch } : c)))
  }

  function adicionarCampo() {
    setCampos((atual) => [...atual, campoVazio(atual.length)])
  }

  const TEMPLATE_DADOS_BASICOS: { mapeamento: NonNullable<CampoPersonalizadoRequest['mapeamento']>; campo: Omit<CampoPersonalizadoRequest, 'id' | 'ordem' | 'mapeamento'> }[] = [
    { mapeamento: 'IDADE', campo: { label: 'Idade', placeholder: 'Ex.: 24', tipo: 'TEXTO_CURTO', opcoes: null, obrigatorio: false, visivelAoPublico: true } },
    { mapeamento: 'ESTADO_CIVIL', campo: { label: 'Estado civil', placeholder: null, tipo: 'OPCAO_UNICA', opcoes: ['Solteiro(a)', 'Casado(a)', 'Divorciado(a)', 'Viúvo(a)'], obrigatorio: false, visivelAoPublico: true } },
    { mapeamento: 'SEXO', campo: { label: 'Sexo', placeholder: null, tipo: 'OPCAO_UNICA', opcoes: ['Homem', 'Mulher'], obrigatorio: false, visivelAoPublico: true } },
    { mapeamento: 'ENDERECO', campo: { label: 'Endereço', placeholder: 'Rua, número, bairro, cidade', tipo: 'TEXTO_CURTO', opcoes: null, obrigatorio: false, visivelAoPublico: true } },
  ]

  // Idempotente: clicar de novo só adiciona o que ainda falta do template (comparando pelo
  // mapeamento, não pelo label — o admin pode ter renomeado). Se os 4 já existem, não faz nada.
  function usarTemplateDadosBasicos() {
    setCampos((atual) => {
      const mapeamentosExistentes = new Set(atual.map((c) => c.mapeamento).filter(Boolean))
      const faltando = TEMPLATE_DADOS_BASICOS.filter((t) => !mapeamentosExistentes.has(t.mapeamento))
      if (faltando.length === 0) return atual

      const base = atual.length
      const novos: CampoPersonalizadoRequest[] = faltando.map((t, i) => ({
        id: null, ordem: base + i, mapeamento: t.mapeamento, ...t.campo,
      }))
      return [...atual, ...novos]
    })
  }

  function removerCampo(indice: number) {
    setCampos((atual) => atual.filter((_, i) => i !== indice).map((c, i) => ({ ...c, ordem: i })))
  }

  return (
    <div className={styles.wrap}>
      <div className={styles.colunaEditor}>
        <p className={styles.introducao}>
          Pergunte algo extra pra quem se inscrever neste evento — tamanho de camiseta,
          restrição alimentar, o que quiser. Cada pergunta vira um campo do formulário.
        </p>

        <p className={styles.avisoConvidados}>
          Estes campos também aparecem pra quem se inscreve sem cadastro no sistema
          (convidados) — pense no que você gostaria de saber dessas pessoas.
        </p>

        <button type="button" className={styles.botaoTemplate} onClick={usarTemplateDadosBasicos}>
          Usar template de dados básicos
        </button>
        <p className={styles.dicaTemplate}>
          Use quando precisar de idade, estado civil, sexo ou endereço de quem vai. Criar cada
          um desses campos na mão faz com que quem já é cadastrado ter que digitar de novo um dado
          que o sistema já tem.
        </p>

        {campos.map((campo, indice) => (
          <div key={campo.id ?? `novo-${indice}`} className={styles.cartaoCampo}>
            <div className={styles.cabecalhoCartao}>
              <span className={styles.numeroCampo}>
                <GripVertical size={14} className={styles.iconeArraste} aria-hidden="true" />
                Pergunta {indice + 1}
                {campo.mapeamento && (
                  <span className={styles.seloMapeado} title="Pula a pergunta pra quem já tem esse dado no cadastro">
                    do template
                  </span>
                )}
              </span>
              <button type="button" className={styles.botaoRemover} onClick={() => removerCampo(indice)} title="Remover esta pergunta">
                <Trash2 size={14} />
              </button>
            </div>

            <div>
              <Input
                id={`campo-label-${indice}`}
                label="O que você quer perguntar"
                placeholder="Ex.: Tamanho da camiseta"
                value={campo.label}
                onChange={(e) => atualizarCampo(indice, { label: e.target.value })}
              />
              <span className={styles.dica}>É o título da pergunta — quem responde vê exatamente esse texto.</span>
            </div>

            <div>
              <Input
                id={`campo-placeholder-${indice}`}
                label="Exemplo de resposta (opcional)"
                placeholder="Ex.: Digite P, M ou G"
                value={campo.placeholder ?? ''}
                onChange={(e) => atualizarCampo(indice, { placeholder: e.target.value || null })}
              />
              <span className={styles.dica}>Aparece apagado dentro do campo, só como exemplo — não é a resposta.</span>
            </div>

            <div>
              <Select
                id={`campo-tipo-${indice}`}
                label="Como a pessoa vai responder"
                options={TIPOS}
                value={campo.tipo}
                onChange={(e) => atualizarCampo(indice, { tipo: e.target.value as TipoCampoPersonalizado })}
              />
              <span className={styles.dica}>{ajudaDoTipo(campo.tipo)}</span>
            </div>

            {(campo.tipo === 'OPCAO_UNICA' || campo.tipo === 'MULTIPLA_ESCOLHA') && (
              <div className={styles.campoOpcoes}>
                <span className={styles.labelOpcoes}>Opções da lista (uma por linha)</span>
                <textarea
                  className={styles.textareaOpcoes}
                  placeholder={'Ex.:\nP\nM\nG'}
                  value={(campo.opcoes ?? []).join('\n')}
                  onChange={(e) => atualizarCampo(indice, {
                    // Sem trim/filter aqui: filtrar linha vazia a cada tecla apagava a
                    // quebra de linha que a pessoa acabou de digitar (Enter "não fazia nada").
                    // Limpeza (trim + remover linha vazia) só acontece ao salvar.
                    opcoes: e.target.value.split('\n'),
                  })}
                />
                <span className={styles.dica}>Cada linha vira uma opção diferente na lista.</span>
              </div>
            )}

            <label className={styles.toggleLinha}>
              <input
                type="checkbox"
                checked={campo.obrigatorio}
                onChange={(e) => atualizarCampo(indice, { obrigatorio: e.target.checked })}
              />
              <span>
                Obrigatório
                <span className={styles.dicaInline}> — quem se inscrever precisa responder essa pergunta</span>
              </span>
            </label>

          </div>
        ))}

        <button type="button" className={styles.botaoAdicionar} onClick={adicionarCampo}>
          <Plus size={16} /> Adicionar pergunta
        </button>

        <p className={styles.dicaSalvar}>
          As perguntas são salvas junto com o resto do evento, no botão de salvar no final
          da página.
        </p>
      </div>

      <PreviaInterativa campos={campos} />
    </div>
  )
})

/** Prévia de verdade, não só ilustrativa: os campos respondem a clique/digitação, com estado
 *  próprio (nunca é enviado a lugar nenhum) — deixa o admin realmente sentir como vai ficar. */
function PreviaInterativa({ campos }: { campos: CampoPersonalizadoRequest[] }) {
  const [valores, setValores] = useState<Record<string, string | string[]>>({})

  function valorTexto(chave: string) {
    const v = valores[chave]
    return typeof v === 'string' ? v : ''
  }

  function valorLista(chave: string) {
    const v = valores[chave]
    return Array.isArray(v) ? v : []
  }

  return (
    <div className={styles.colunaPreview}>
      <div className={styles.cabecalhoPreview}>
        <span className={styles.tituloPreview}>Prévia — clique e preencha pra testar</span>
        <button type="button" className={styles.botaoLimparPreview} onClick={() => setValores({})}>
          <RotateCcw size={12} /> Limpar
        </button>
      </div>

      {/* Interativos igual aos outros campos (prévia de verdade, nunca disabled — ver
          CLAUDE.md), mas nunca viram CampoPersonalizadoRequest de verdade: nome/telefone já
          são sempre coletados automaticamente (titular/convidado). Só pra quem está montando
          o formulário sentir como fica completo, digitando algo real se quiser. */}
      <div className={styles.previewCampo}>
        <label className={styles.previewLabel}>Nome</label>
        <input
          placeholder="Ex.: Maria Souza"
          value={valorTexto('__nome')}
          onChange={(e) => setValores((v) => ({ ...v, __nome: e.target.value }))}
        />
        <span className={styles.dica}>Sempre coletado automaticamente — aqui é só pra você testar.</span>
      </div>
      <div className={styles.previewCampo}>
        <label className={styles.previewLabel}>Telefone</label>
        <input
          placeholder="(00) 00000-0000"
          value={valorTexto('__telefone')}
          onChange={(e) => setValores((v) => ({ ...v, __telefone: e.target.value }))}
        />
        <span className={styles.dica}>Sempre coletado automaticamente — aqui é só pra você testar.</span>
      </div>

      {campos.length === 0 && (
        <p className={styles.previewVazio}>Adicione uma pergunta pra ver como fica aqui.</p>
      )}

      {campos.map((campo, indice) => {
        const chave = campo.id ?? `novo-${indice}`
        return (
          <div key={chave} className={styles.previewCampo}>
            <label className={styles.previewLabel}>
              {campo.label || 'O que você quer perguntar'}{campo.obrigatorio && <span className={styles.previewAsterisco}> *</span>}
            </label>

            {campo.tipo === 'TEXTO_CURTO' && (
              <input
                placeholder={campo.placeholder ?? ''}
                value={valorTexto(chave)}
                onChange={(e) => setValores((v) => ({ ...v, [chave]: e.target.value }))}
              />
            )}

            {campo.tipo === 'SIM_NAO' && (
              <div className={styles.previewSegmentado}>
                <button
                  type="button"
                  className={`${styles.previewSegmentoBtn} ${valorTexto(chave) === 'Sim' ? styles.previewSegmentoAtivo : ''}`}
                  onClick={() => setValores((v) => ({ ...v, [chave]: 'Sim' }))}
                >
                  Sim
                </button>
                <button
                  type="button"
                  className={`${styles.previewSegmentoBtn} ${valorTexto(chave) === 'Não' ? styles.previewSegmentoAtivo : ''}`}
                  onClick={() => setValores((v) => ({ ...v, [chave]: 'Não' }))}
                >
                  Não
                </button>
              </div>
            )}

            {campo.tipo === 'OPCAO_UNICA' && (
              limparOpcoes(campo.opcoes).length === 0 ? (
                <span className={styles.previewVazio}>Adicione opções pra testar.</span>
              ) : (
                <select
                  value={valorTexto(chave)}
                  onChange={(e) => setValores((v) => ({ ...v, [chave]: e.target.value }))}
                >
                  <option value="">Selecione</option>
                  {limparOpcoes(campo.opcoes).map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
              )
            )}

            {campo.tipo === 'MULTIPLA_ESCOLHA' && (
              <div className={styles.previewOpcoesMultiplas}>
                {limparOpcoes(campo.opcoes).length === 0 && (
                  <span className={styles.previewVazio}>Adicione opções pra testar.</span>
                )}
                {limparOpcoes(campo.opcoes).map((o) => {
                  const selecionadas = valorLista(chave)
                  const marcado = selecionadas.includes(o)
                  return (
                    <label key={o} className={styles.previewCheckboxLinha}>
                      <input
                        type="checkbox"
                        checked={marcado}
                        onChange={() => {
                          const novas = marcado ? selecionadas.filter((s) => s !== o) : [...selecionadas, o]
                          setValores((v) => ({ ...v, [chave]: novas }))
                        }}
                      />
                      {o}
                    </label>
                  )
                })}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
