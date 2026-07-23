import React, { forwardRef } from 'react'
import { Check } from 'lucide-react'
import styles from './StatusCards.module.css'

interface StatusOption {
  value: string
  titulo: string
  /**
   * Subtítulo da opção. OPCIONAL: há escolhas que se explicam sozinhas ("Homem"/"Mulher")
   * e forçá-las a inventar uma frase só para preencher o campo produziria ruído. Quando
   * ausente, o elemento nem é renderizado — um span vazio continuaria contando como item
   * de flex e somaria o `gap` do container.
   */
  descricao?: string
}

interface StatusCardsProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  options: StatusOption[]
  // valor atual selecionado, pra marcar o cartão ativo visualmente
  selecionado?: string
}

export const StatusCards = forwardRef<HTMLInputElement, StatusCardsProps>(
  function StatusCards({ label, options, selecionado, name, ...props }, ref) {
    return (
      <div className={styles.group}>
        {label && <span className={styles.label}>{label}</span>}
        <div className={styles.cards}>
          {options.map((opt) => {
            const ativo = selecionado === opt.value
            return (
              <label
                key={opt.value}
                className={`${styles.card} ${ativo ? styles.cardAtivo : ''}`}
              >
                <input
                  ref={ref}
                  type="radio"
                  name={name}
                  value={opt.value}
                  className={styles.radioHidden}
                  {...props}
                />
                <span className={styles.texto}>
                  <span className={styles.titulo}>{opt.titulo}</span>
                  {opt.descricao && (
                    <span className={styles.descricao}>{opt.descricao}</span>
                  )}
                </span>
                {ativo && (
                  <span className={styles.checkIcon}>
                    <Check size={18} strokeWidth={3} />
                  </span>
                )}
              </label>
            )
          })}
        </div>
      </div>
    )
  },
)

StatusCards.displayName = 'StatusCards'