'use client'

import { useEffect, useId, useRef, useState } from 'react'
import { AlertTriangle, X, Check } from 'lucide-react'
import styles from './ModalConfirmacaoCritica.module.css'

export interface Consequencia {
  /** `perde` = algo deixa de existir/aparecer; `mantem` = tranquiliza sobre o que NÃO muda. */
  tipo: 'perde' | 'mantem'
  texto: string
}

interface Props {
  titulo: string
  mensagem: React.ReactNode
  consequencias?: Consequencia[]
  /** O que a pessoa precisa digitar. Comparado sem diferenciar maiúsculas nem espaços nas pontas. */
  palavraConfirmacao: string
  textoConfirmar?: string
  isLoading?: boolean
  erro?: string | null
  onConfirmar: () => void
  onClose: () => void
}

/**
 * Confirmação para ações destrutivas, no modelo "digite o nome para confirmar" do GitHub.
 *
 * <p>A digitação não existe para ser chata: ela força a pessoa a <b>ler qual</b> item está
 * prestes a ser afetado. Num clique duplo distraído, um `confirm()` comum já teria passado.
 * Use só onde o estrago é real — em ação reversível, isto vira atrito à toa.
 */
export function ModalConfirmacaoCritica({
  titulo,
  mensagem,
  consequencias = [],
  palavraConfirmacao,
  textoConfirmar = 'Confirmar',
  isLoading = false,
  erro = null,
  onConfirmar,
  onClose,
}: Props) {
  const [digitado, setDigitado] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const formRef = useRef<HTMLFormElement>(null)
  const inputId = useId()

  // Compara ignorando acento e caixa ("retiro de jovens" bate com "Retiro de Jovens") —
  // a exigência de digitar o nome continua, só a rigidez da comparação afrouxa.
  const normalizar = (v: string) =>
    v.trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[̀-ͯ]/g, '')

  const confere = normalizar(digitado) === normalizar(palavraConfirmacao)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) {
        onClose()
        return
      }
      if (e.key !== 'Tab' || !formRef.current) return

      // Tab escaparia do diálogo pro conteúdo de fundo sem isto — prende o foco nos elementos focáveis do modal.
      const focaveis = formRef.current.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), [href], select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
      )
      if (focaveis.length === 0) return

      const primeiro = focaveis[0]
      const ultimo = focaveis[focaveis.length - 1]

      if (e.shiftKey && document.activeElement === primeiro) {
        e.preventDefault()
        ultimo.focus()
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault()
        primeiro.focus()
      }
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, isLoading])

  function aoEnviar(e: React.FormEvent) {
    e.preventDefault()
    if (confere && !isLoading) onConfirmar()
  }

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <form
        ref={formRef}
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={aoEnviar}
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${inputId}-titulo`}
      >
        <div className={styles.cabecalho}>
          <span className={styles.iconBox}>
            <AlertTriangle size={22} aria-hidden="true" />
          </span>
          <h2 className={styles.titulo} id={`${inputId}-titulo`}>{titulo}</h2>
        </div>

        <div className={styles.corpo}>
          <p className={styles.mensagem}>{mensagem}</p>

          {consequencias.length > 0 && (
            <div className={styles.consequencias}>
              {consequencias.map((c, i) => (
                <p key={i} className={styles.consequencia}>
                  {c.tipo === 'perde' ? (
                    <X size={14} className={styles.iconePerde} aria-hidden="true" />
                  ) : (
                    <Check size={14} className={styles.iconeMantem} aria-hidden="true" />
                  )}
                  {c.texto}
                </p>
              ))}
            </div>
          )}

          <div className={styles.blocoConfirmacao}>
            <label className={styles.instrucao} htmlFor={inputId} id={`${inputId}-ajuda`}>
              Para confirmar, digite{' '}
              <span className={styles.palavraChave}>{palavraConfirmacao}</span> abaixo:
            </label>
            <input
              id={inputId}
              ref={inputRef}
              className={styles.input}
              value={digitado}
              onChange={(e) => setDigitado(e.target.value)}
              disabled={isLoading}
              autoComplete="off"
              aria-describedby={`${inputId}-ajuda`}
            />
          </div>
        </div>

        {erro && <p className={styles.erro}>{erro}</p>}

        <div className={styles.rodape}>
          <button
            type="button"
            className={styles.btnCancelar}
            onClick={onClose}
            disabled={isLoading}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className={styles.btnConfirmar}
            disabled={!confere || isLoading}
          >
            {isLoading ? 'Processando…' : textoConfirmar}
          </button>
        </div>
      </form>
    </div>
  )
}
