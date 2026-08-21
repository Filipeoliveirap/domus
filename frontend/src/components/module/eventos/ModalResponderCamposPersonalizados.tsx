'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { ClipboardList } from 'lucide-react'
import { Button } from '@/components/common/button/Button'
import { Input } from '@/components/common/input/Input'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './ModalResponderCamposPersonalizados.module.css'
import type { CampoPersonalizadoResponse, RespostaResponse } from '@/types/campoPersonalizado.type'

interface Props {
  inscricaoId: string
  acompanhanteId?: string
  campos: CampoPersonalizadoResponse[]
  respostasIniciais: RespostaResponse[]
  onClose: () => void
  onSalvo: () => void
}

export function ModalResponderCamposPersonalizados({
  inscricaoId, acompanhanteId, campos, respostasIniciais, onClose, onSalvo,
}: Props) {
  const { responder, isLoading, erro } = useResponderCampos()
  const [valores, setValores] = useState<Record<string, string>>(
    () => Object.fromEntries(respostasIniciais.map((r) => [r.campoId, r.valor])),
  )
  // Só mostra erro por campo depois da primeira tentativa de salvar — não antes, enquanto a
  // pessoa ainda está preenchendo pela primeira vez.
  const [tentouSalvar, setTentouSalvar] = useState(false)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  const pendentes = campos.filter((c) => c.obrigatorio && !(valores[c.id]?.trim()))

  async function aoSalvar() {
    setTentouSalvar(true)
    // Valida no cliente antes de chamar o back: assim o erro aparece só junto do campo que
    // falta, sem round-trip nem toast genérico pra um caso que a própria tela já sabia.
    if (pendentes.length > 0) return

    const dados = campos.map((c) => ({ campoId: c.id, valor: valores[c.id] ?? '' }))
    const sucesso = await responder(inscricaoId, dados, acompanhanteId)
    if (sucesso) onSalvo()
  }

  function erroDoCampo(campo: CampoPersonalizadoResponse): string | undefined {
    if (!tentouSalvar || !campo.obrigatorio) return undefined
    return valores[campo.id]?.trim() ? undefined : 'Essa pergunta é obrigatória.'
  }

  // Portal pro <body>: sem isso, o modal nasce dentro do drawer (que abre com um
  // transform de deslize) e `position: fixed` passa a ser relativo a esse ancestral
  // transformado em vez do viewport — o modal aparecia preso no canto até o transform
  // do drawer terminar de animar.
  if (typeof document === 'undefined') return null

  return createPortal(
    <div className={baseStyles.overlay} onMouseDown={onClose}>
      <div
        className={`${baseStyles.modal} ${styles.modalLargo}`}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-responder-campos-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <ClipboardList size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-responder-campos-titulo">
            Perguntas do evento
          </h2>
        </div>

        <div className={`${baseStyles.corpo} ${styles.corpoScroll}`}>
          {campos.map((campo) => {
            const mensagemErro = erroDoCampo(campo)
            return (
              <div key={campo.id} className={styles.campo}>
                {campo.tipo === 'SIM_NAO' ? (
                  <div>
                    <span className={styles.label}>{campo.label}{campo.obrigatorio && <span className={styles.asterisco}> *</span>}</span>
                    <div className={`${styles.segmentado} ${mensagemErro ? styles.comErro : ''}`}>
                      <button
                        type="button"
                        className={`${styles.segmentoBtn} ${valores[campo.id] === 'Sim' ? styles.segmentoAtivo : ''}`}
                        onClick={() => setValores((v) => ({ ...v, [campo.id]: 'Sim' }))}
                      >
                        Sim
                      </button>
                      <button
                        type="button"
                        className={`${styles.segmentoBtn} ${valores[campo.id] === 'Não' ? styles.segmentoAtivo : ''}`}
                        onClick={() => setValores((v) => ({ ...v, [campo.id]: 'Não' }))}
                      >
                        Não
                      </button>
                    </div>
                  </div>
                ) : campo.tipo === 'OPCAO_UNICA' ? (
                  <div>
                    <span className={styles.label}>{campo.label}{campo.obrigatorio && <span className={styles.asterisco}> *</span>}</span>
                    <select
                      className={mensagemErro ? styles.comErro : ''}
                      value={valores[campo.id] ?? ''}
                      onChange={(e) => setValores((v) => ({ ...v, [campo.id]: e.target.value }))}
                    >
                      <option value="">Selecione</option>
                      {campo.opcoes.map((o) => <option key={o} value={o}>{o}</option>)}
                    </select>
                  </div>
                ) : campo.tipo === 'MULTIPLA_ESCOLHA' ? (
                  <div>
                    <span className={styles.label}>{campo.label}{campo.obrigatorio && <span className={styles.asterisco}> *</span>}</span>
                    <div className={styles.opcoesMultiplas}>
                      {campo.opcoes.map((o) => {
                        const selecionadas = (valores[campo.id] ?? '').split(' | ').filter(Boolean)
                        const marcado = selecionadas.includes(o)
                        return (
                          <label key={o} className={styles.checkboxLinha}>
                            <input
                              type="checkbox"
                              checked={marcado}
                              onChange={() => {
                                const novas = marcado ? selecionadas.filter((s) => s !== o) : [...selecionadas, o]
                                setValores((v) => ({ ...v, [campo.id]: novas.join(' | ') }))
                              }}
                            />
                            {o}
                          </label>
                        )
                      })}
                    </div>
                  </div>
                ) : (
                  <Input
                    id={`resposta-${campo.id}`}
                    label={campo.label}
                    labelRight={campo.obrigatorio ? <span className={styles.asterisco}>obrigatório</span> : undefined}
                    placeholder={campo.placeholder ?? undefined}
                    value={valores[campo.id] ?? ''}
                    onChange={(e) => setValores((v) => ({ ...v, [campo.id]: e.target.value }))}
                    error={mensagemErro}
                  />
                )}
                {/* Input já mostra o próprio erro (prop `error`); os outros tipos de campo
                    (sem componente próprio de erro) mostram aqui, logo abaixo do controle. */}
                {mensagemErro && campo.tipo !== 'TEXTO_CURTO' && (
                  <span className={styles.erroCampo}>{mensagemErro}</span>
                )}
              </div>
            )
          })}

          {erro && <p className={styles.erro}>{erro}</p>}
        </div>

        <div className={baseStyles.rodape}>
          <button type="button" className={baseStyles.btnCancelar} onClick={onClose}>
            Fechar
          </button>
          <Button type="button" onClick={aoSalvar} disabled={isLoading}>
            {isLoading ? 'Salvando…' : 'Salvar respostas'}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
