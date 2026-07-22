import { Input } from '@/components/common/input/Input'
import styles from './MinisterioInput.module.css'

const SUGESTOES = [
  'Louvor e Adoração',
  'Ministério Infantil',
  'Jovens e Adolescentes',
  'Acolhimento e Recepção',
  'Missões e Evangelismo',
]

interface MinisterioInputProps {
  id: string
  label?: string
  value?: string
  error?: string
  registerProps: React.InputHTMLAttributes<HTMLInputElement>
  onSelecionarSugestao: (valor: string) => void
}

export function MinisterioInput({
  id, label, value = '', error, registerProps, onSelecionarSugestao,
}: MinisterioInputProps) {
  return (
    <div className={styles.wrapper}>
      <Input
        id={id}
        label={label}
        placeholder="Digite ou escolha abaixo"
        error={error}
        {...registerProps}
      />
      <div className={styles.chips}>
        {SUGESTOES.map((sugestao) => (
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
    </div>
  )
}