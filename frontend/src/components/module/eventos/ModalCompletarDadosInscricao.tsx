'use client'

import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { ClipboardList, X } from 'lucide-react'
import { Button } from '@/components/common/button/Button'
import { Input } from '@/components/common/input/Input'
import { CamposExtrasForm } from './CamposExtrasForm'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './ModalCompletarDadosInscricao.module.css'
import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'

interface Props {
  /** Nome de quem está completando os dados — só pra personalizar o título. */
  nome?: string
  /** `true` = pede e-mail (a pessoa ainda não tem um cadastrado). */
  pedeEmail: boolean
  campos: CampoPersonalizadoResponse[]
  isLoading: boolean
  onConfirmar: (dados: { email: string | null; respostas: Record<string, string> }) => void
  /** Fecha só esta etapa (ex.: pula esta pessoa da fila e segue pra próxima). */
  onClose: () => void
  /** Fecha tudo de uma vez (o X e clicar fora usam este, não `onClose`) — numa fila de
   *  várias pessoas, sem isto o admin tinha que "Cancelar" uma por uma pra sair. */
  onFecharTudo: () => void
}

/**
 * E-mail virou obrigatório pra se inscrever em qualquer evento (2026-08-27) — quem ainda
 * não tem cadastra aqui, no mesmo passo em que responde os campos personalizados do
 * evento (se houver), em vez de ser barrado e mandado pro perfil. Mesmo padrão visual dos
 * outros modais de formulário do projeto (reaproveita ModalConfirmacao + CamposExtrasForm).
 */
export function ModalCompletarDadosInscricao({
  nome, pedeEmail, campos, isLoading, onConfirmar, onClose, onFecharTudo,
}: Props) {
  const [email, setEmail] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouEnviar, setTentouEnviar] = useState(false)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !isLoading) onFecharTudo() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onFecharTudo, isLoading])

  const emailValido = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
  const camposObrigatoriosPendentes = campos.some(
    (c) => c.obrigatorio && !(camposValores[c.id]?.trim()),
  )

  function aoConfirmar() {
    if ((pedeEmail && !emailValido) || camposObrigatoriosPendentes) {
      setTentouEnviar(true)
      return
    }
    onConfirmar({ email: pedeEmail ? email.trim() : null, respostas: camposValores })
  }

  if (typeof document === 'undefined') return null

  const titulo = pedeEmail && campos.length === 0
    ? (nome ? `É preciso do e-mail de ${nome} para inscrever no evento` : 'É preciso do seu e-mail para se inscrever no evento')
    : (nome ? `Falta completar os dados de ${nome}` : 'Falta completar seus dados')

  return createPortal(
    <div className={baseStyles.overlay} onMouseDown={() => !isLoading && onFecharTudo()}>
      <div
        className={baseStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-completar-dados-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <ClipboardList size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-completar-dados-titulo">
            {titulo}
          </h2>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onFecharTudo}
            disabled={isLoading}
            aria-label="Fechar"
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <div className={`${baseStyles.corpo} ${styles.corpo}`}>
          {pedeEmail && (
            <Input
              id="email-completar-inscricao"
              type="email"
              label="E-mail"
              placeholder="nome@exemplo.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={tentouEnviar && !emailValido ? 'Informe um e-mail válido.' : undefined}
            />
          )}
          <CamposExtrasForm
            campos={campos}
            valores={camposValores}
            onChange={(campoId, valor) => setCamposValores((v) => ({ ...v, [campoId]: valor }))}
            tentouEnviar={tentouEnviar}
          />
        </div>

        <div className={baseStyles.rodape}>
          <button type="button" className={baseStyles.btnCancelar} onClick={onClose} disabled={isLoading}>
            Cancelar
          </button>
          <Button type="button" onClick={aoConfirmar} isLoading={isLoading}>
            Confirmar
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
