'use client'

import { useState } from 'react'
import { Plus, Trash2, GripVertical, RotateCcw } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { Button } from '@/components/common/button/Button'
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

/** Trim + remove linha vazia — só na hora de usar (salvar ou renderizar opção), nunca
 *  enquanto a pessoa ainda está digitando (ver comentário no onChange da textarea). */
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

export function CamposPersonalizadosPainel({ eventoId }: { eventoId: string }) {
  const { data, isLoading } = useCamposPersonalizados(eventoId)

  if (isLoading || !data) return null

  // key={eventoId} garante que o estado local reinicia dos dados do servidor sempre que o
  // evento muda — sem precisar de useEffect pra sincronizar query -> estado editável.
  return <PainelEditor key={eventoId} eventoId={eventoId} dadosIniciais={data} />
}

function PainelEditor({ eventoId, dadosIniciais }: { eventoId: string; dadosIniciais: CampoPersonalizadoResponse[] }) {
  const { salvar, isLoading: salvando } = useSalvarCamposPersonalizados()
  const [campos, setCampos] = useState<CampoPersonalizadoRequest[]>(() => dadosIniciais.map(paraRequest))

  function atualizarCampo(indice: number, patch: Partial<CampoPersonalizadoRequest>) {
    setCampos((atual) => atual.map((c, i) => {
      if (i !== indice) return c
      // Mexeu em tipo ou opções de um campo vindo do template: perde o mapeamento — ele só
      // garante "pular pergunta pra quem já tem o dado" enquanto a estrutura for a do
      // template original. Editar só o rótulo não conta como mudar estrutura.
      const mexeuNaEstrutura = 'tipo' in patch || 'opcoes' in patch
      return { ...c, ...patch, mapeamento: mexeuNaEstrutura && c.mapeamento ? null : c.mapeamento }
    }))
  }

  function adicionarCampo() {
    setCampos((atual) => [...atual, campoVazio(atual.length)])
  }

  /** Adiciona de uma vez os 4 campos que o Domus já trata como dado estruturado de Pessoa —
   *  cada um marcado com `mapeamento`, que faz o backend pular a pergunta pra quem já tem o
   *  dado no cadastro (ver CampoPersonalizadoService.listarParaResponder). */
  function usarTemplateDadosBasicos() {
    setCampos((atual) => {
      const base = atual.length
      const novos: CampoPersonalizadoRequest[] = [
        {
          id: null, label: 'Idade', placeholder: 'Ex.: 24', tipo: 'TEXTO_CURTO', opcoes: null,
          obrigatorio: false, visivelAoPublico: true, ordem: base, mapeamento: 'IDADE',
        },
        {
          id: null, label: 'Estado civil', placeholder: null, tipo: 'OPCAO_UNICA',
          opcoes: ['Solteiro(a)', 'Casado(a)', 'Divorciado(a)', 'Viúvo(a)'],
          obrigatorio: false, visivelAoPublico: true, ordem: base + 1, mapeamento: 'ESTADO_CIVIL',
        },
        {
          id: null, label: 'Sexo', placeholder: null, tipo: 'OPCAO_UNICA', opcoes: ['Homem', 'Mulher'],
          obrigatorio: false, visivelAoPublico: true, ordem: base + 2, mapeamento: 'SEXO',
        },
        {
          id: null, label: 'Endereço', placeholder: 'Rua, número, bairro, cidade', tipo: 'TEXTO_CURTO',
          opcoes: null, obrigatorio: false, visivelAoPublico: true, ordem: base + 3, mapeamento: 'ENDERECO',
        },
      ]
      return [...atual, ...novos]
    })
  }

  function removerCampo(indice: number) {
    setCampos((atual) => atual.filter((_, i) => i !== indice).map((c, i) => ({ ...c, ordem: i })))
  }

  async function aoSalvar() {
    const camposLimpos = campos.map((c) => ({ ...c, opcoes: c.opcoes ? limparOpcoes(c.opcoes) : null }))
    await salvar(eventoId, camposLimpos)
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

            {/* Sem efeito ainda — groundwork pro formulário público (spec futura). */}
            <label className={styles.toggleLinha} title="Ainda sem efeito — chega com o formulário público, pra inscrição de gente sem cadastro">
              <input type="checkbox" checked={campo.visivelAoPublico} disabled />
              <span>
                Visível ao público
                <span className={styles.dicaInline}> — em breve, pra quando existir link público de inscrição</span>
              </span>
            </label>
          </div>
        ))}

        <button type="button" className={styles.botaoAdicionar} onClick={adicionarCampo}>
          <Plus size={16} /> Adicionar pergunta
        </button>

        <Button type="button" onClick={aoSalvar} disabled={salvando}>
          {salvando ? 'Salvando…' : 'Salvar campos personalizados'}
        </Button>
      </div>

      <PreviaInterativa campos={campos} />
    </div>
  )
}

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

      {/* Nunca campo de verdade — nome/telefone são sempre coletados automaticamente
          (titular/convidado), não fazem parte da lista de CampoPersonalizadoRequest. Só pra
          quem está montando o formulário ver como fica completo. */}
      <div className={styles.previewCampo}>
        <label className={styles.previewLabel}>Nome</label>
        <input disabled placeholder="Sempre coletado automaticamente" />
      </div>
      <div className={styles.previewCampo}>
        <label className={styles.previewLabel}>Telefone</label>
        <input disabled placeholder="Sempre coletado automaticamente" />
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
