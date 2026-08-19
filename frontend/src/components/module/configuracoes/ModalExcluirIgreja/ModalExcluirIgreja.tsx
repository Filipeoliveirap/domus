'use client'

import { useEffect, useId, useRef, useState } from 'react'
import axios from 'axios'
import { AlertTriangle, X, CheckCircle2 } from 'lucide-react'
import { GoogleOAuthProvider, GoogleLogin } from '@react-oauth/google'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { ApiError } from '@/types/api.types'
import type { ResumoExclusao } from '@/types/exclusaoIgreja.types'
import styles from './ModalExcluirIgreja.module.css'

interface Props {
  nomeIgreja: string
  onClose: () => void
  onExcluidoComSucesso: () => void
}

/**
 * Modal de confirmação de exclusão da igreja. Segue a linguagem visual de
 * `ModalConfirmacaoCritica` (mesmo overlay, mesmo "digite o nome"), mas é componente
 * próprio porque tem responsabilidade extra: buscar o resumo do que será apagado e
 * exigir reautenticação antes de agendar a exclusão.
 *
 * O resumo (`GET /igrejas/exclusao/resumo`) já traz `temSenhaNativa`, calculado pelo
 * backend a partir de `usuario.senhaHash`. Conta nativa vê o campo de senha; conta
 * só-Google (`senhaHash == null`) vê o botão "Confirmar com Google" em vez disso.
 */
export function ModalExcluirIgreja({ nomeIgreja, onClose, onExcluidoComSucesso }: Props) {
  const [resumo, setResumo] = useState<ResumoExclusao | null>(null)
  const [digitado, setDigitado] = useState('')
  const [senha, setSenha] = useState('')
  const [googleIdToken, setGoogleIdToken] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const inputId = useId()
  const googleClientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID as string

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    api.get<ResumoExclusao>(Endpoints.igreja.exclusao.RESUMO)
      .then((res) => setResumo(res.data))
      .catch(() => setErro('Não foi possível carregar o resumo. Tente novamente.'))
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !carregando) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, carregando])

  // Compara ignorando acento e caixa, no mesmo padrão de ModalConfirmacaoCritica.
  const normalizar = (v: string) =>
    v.trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[̀-ͯ]/g, '')

  const confere = normalizar(digitado) === normalizar(nomeIgreja)
  const reautenticado = resumo?.temSenhaNativa ? senha.length > 0 : googleIdToken !== null

  async function confirmar() {
    setCarregando(true)
    setErro(null)
    try {
      await api.post(Endpoints.igreja.exclusao.AGENDAR, {
        nomeConfirmacao: digitado,
        senha: resumo?.temSenhaNativa ? senha : undefined,
        googleIdToken: resumo?.temSenhaNativa ? undefined : googleIdToken ?? undefined,
      })
      onExcluidoComSucesso()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const codigo = error.response?.data?.error
        if (codigo === 'REAUTENTICACAO_INVALIDA') {
          setGoogleIdToken(null)
          setErro('A conta do Google que você confirmou é diferente da conta usada neste login. '
            + 'Entre com a mesma conta Google do seu login para confirmar a exclusão.')
        } else if (codigo === 'REAUTENTICACAO_NECESSARIA') {
          setErro('Confirme sua identidade antes de continuar.')
        } else {
          setErro(error.response?.data?.message ?? 'Não foi possível agendar a exclusão. Tente novamente.')
        }
      } else {
        setErro('Não foi possível agendar a exclusão. Tente novamente.')
      }
    } finally {
      setCarregando(false)
    }
  }

  function aoEnviar(e: React.FormEvent) {
    e.preventDefault()
    if (confere && reautenticado && !carregando) confirmar()
  }

  return (
    <div className={styles.overlay} onMouseDown={() => !carregando && onClose()}>
      <form
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
          <h2 className={styles.titulo} id={`${inputId}-titulo`}>Excluir esta igreja</h2>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            disabled={carregando}
            aria-label="Fechar"
          >
            <X size={18} />
          </button>
        </div>

        <div className={styles.corpo}>
          <p className={styles.mensagem}>
            Isso vai apagar definitivamente
            {resumo ? (
              <>
                {' '}<strong>{resumo.pessoas} pessoas</strong>,{' '}
                <strong>{resumo.eventos} eventos</strong>,{' '}
                <strong>{resumo.movimentacoesFinanceiras} movimentações financeiras</strong>,{' '}
                <strong>{resumo.celulas} células</strong>,{' '}
                <strong>{resumo.ministerios} ministérios</strong> e{' '}
                <strong>{resumo.usuarios} usuários</strong>.
              </>
            ) : ' …'}
          </p>

          {resumo && resumo.igrejasVinculadas.length > 0 && (
            <p className={styles.avisoRede}>
              As {resumo.igrejasVinculadas.length} igrejas vinculadas ({resumo.igrejasVinculadas.join(', ')}) vão
              sair da rede — cada uma continua funcionando normalmente, com todos os dados intactos, só deixam de
              estar ligadas a esta.
            </p>
          )}

          <p className={styles.mensagem}>
            Isso é <strong>reversível por 10 dias</strong>. Depois disso, não há como recuperar.
          </p>

          <div className={styles.blocoConfirmacao}>
            <label className={styles.instrucao} htmlFor={inputId}>
              Para confirmar, digite <span className={styles.palavraChave}>{nomeIgreja}</span> abaixo:
            </label>
            <input
              id={inputId}
              ref={inputRef}
              className={styles.input}
              value={digitado}
              onChange={(e) => setDigitado(e.target.value)}
              disabled={carregando}
              autoComplete="off"
            />
          </div>

          {resumo && (
            resumo.temSenhaNativa ? (
              <div className={styles.blocoConfirmacao}>
                <label className={styles.instrucao} htmlFor={`${inputId}-senha`}>
                  Confirme sua senha:
                </label>
                <input
                  id={`${inputId}-senha`}
                  type="password"
                  className={styles.input}
                  value={senha}
                  onChange={(e) => setSenha(e.target.value)}
                  disabled={carregando}
                  autoComplete="current-password"
                />
              </div>
            ) : (
              <div className={styles.blocoConfirmacao}>
                <span className={styles.instrucao}>
                  Sua conta usa login com Google — confirme sua identidade:
                </span>
                {googleIdToken ? (
                  <p className={styles.googleConfirmado}>
                    <CheckCircle2 size={16} aria-hidden="true" /> Identidade confirmada com o Google.
                  </p>
                ) : (
                  <GoogleOAuthProvider clientId={googleClientId}>
                    <GoogleLogin
                      onSuccess={(cred) => {
                        if (cred.credential) setGoogleIdToken(cred.credential)
                      }}
                      onError={() => setErro('Não foi possível confirmar sua identidade com o Google.')}
                      text="continue_with"
                      width="320"
                    />
                  </GoogleOAuthProvider>
                )}
              </div>
            )
          )}
        </div>

        {erro && <p className={styles.erro}>{erro}</p>}

        <div className={styles.rodape}>
          <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={carregando}>
            Cancelar
          </button>
          <button type="submit" className={styles.btnConfirmar} disabled={!confere || !reautenticado || carregando}>
            {carregando ? 'Processando…' : 'Excluir esta igreja'}
          </button>
        </div>
      </form>
    </div>
  )
}
