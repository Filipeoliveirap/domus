'use client'

import type { CampoPersonalizadoResponse } from '@/types/campoPersonalizado.type'
import styles from './CamposExtrasForm.module.css'

interface Props {
  campos: CampoPersonalizadoResponse[]
  valores: Record<string, string>
  onChange: (campoId: string, valor: string) => void
  tentouEnviar: boolean
}

/** Renderiza os campos personalizados do evento (Spec 1) fora do fluxo normal de resposta —
 *  usado no formulário de convidado sem cadastro e na auto-inscrição via convite, onde ainda
 *  não existe inscricaoId pra chamar PUT /inscricoes/{id}/respostas na hora de cada campo. */
export function CamposExtrasForm({ campos, valores, onChange, tentouEnviar }: Props) {
  if (campos.length === 0) return null

  return (
    <div className={styles.lista}>
      {campos.map((campo) => {
        const mensagemErro = tentouEnviar && campo.obrigatorio && !(valores[campo.id]?.trim())
          ? 'Essa pergunta é obrigatória.'
          : undefined

        return (
          <label key={campo.id} className={styles.campo}>
            <span className={styles.label}>
              {campo.label}{campo.obrigatorio && <span className={styles.asterisco}> *</span>}
            </span>

            {campo.tipo === 'OPCAO_UNICA' || campo.tipo === 'SIM_NAO' ? (
              <select
                className={mensagemErro ? styles.comErro : undefined}
                value={valores[campo.id] ?? ''}
                onChange={(e) => onChange(campo.id, e.target.value)}
              >
                <option value="">Selecione…</option>
                {(campo.tipo === 'SIM_NAO' ? ['Sim', 'Não'] : campo.opcoes).map((op) => (
                  <option key={op} value={op}>{op}</option>
                ))}
              </select>
            ) : campo.tipo === 'MULTIPLA_ESCOLHA' ? (
              <div className={styles.opcoesMultiplas}>
                {campo.opcoes.map((op) => {
                  const selecionadas = (valores[campo.id] ?? '').split(' | ').filter(Boolean)
                  const marcado = selecionadas.includes(op)
                  return (
                    <label key={op} className={styles.checkboxLinha}>
                      <input
                        type="checkbox"
                        checked={marcado}
                        onChange={() => {
                          const novas = marcado ? selecionadas.filter((s) => s !== op) : [...selecionadas, op]
                          onChange(campo.id, novas.join(' | '))
                        }}
                      />
                      {op}
                    </label>
                  )
                })}
              </div>
            ) : (
              <input
                type="text"
                className={mensagemErro ? styles.comErro : undefined}
                placeholder={campo.placeholder ?? ''}
                value={valores[campo.id] ?? ''}
                onChange={(e) => onChange(campo.id, e.target.value)}
              />
            )}

            {mensagemErro && <span className={styles.erro}>{mensagemErro}</span>}
          </label>
        )
      })}
    </div>
  )
}
