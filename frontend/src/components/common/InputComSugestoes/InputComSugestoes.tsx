import { Input } from '@/components/common/input/Input'
import styles from './InputComSugestoes.module.css'

interface InputComSugestoesProps {
  id: string
  label?: string
  placeholder?: string
  value?: string
  error?: string
  /**
   * Sugestões vêm por prop (não de constante interna) — é essa diferença que permite
   * reusar o mesmo componente no tipo do evento (GET /eventos/tipos) e no local ad-hoc,
   * cada um com sua própria lista.
   */
  sugestoes: string[]
  registerProps: React.InputHTMLAttributes<HTMLInputElement>
  onSelecionarSugestao: (valor: string) => void
}

// Chips clicáveis + digitação livre: os chips são atalho, nunca bloqueio — digitar algo
// fora da lista sempre funciona (espelha o visual do MinisterioInput).
export function InputComSugestoes({
  id, label, placeholder, value = '', error, sugestoes, registerProps, onSelecionarSugestao,
}: InputComSugestoesProps) {
  return (
    <div className={styles.wrapper}>
      <Input
        id={id}
        label={label}
        placeholder={placeholder ?? 'Digite ou escolha abaixo'}
        error={error}
        {...registerProps}
      />
      {sugestoes.length > 0 && (
        <div className={styles.chips}>
          {sugestoes.map((sugestao) => (
            <button
              key={sugestao}
              type="button"
              className={`${styles.chip} ${value === sugestao ? styles.chipAtivo : ''}`}
              onClick={() => onSelecionarSugestao(sugestao)}
            >
              {sugestao}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
