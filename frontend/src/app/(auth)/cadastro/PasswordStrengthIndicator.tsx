'use client'

import { Check } from 'lucide-react'
import styles from './PasswordStrengthIndicator.module.css'

interface PasswordStrengthIndicatorProps {
  senha: string
}

// Calcula a força da senha de 0 a 4
// 0 = vazia, 1 = fraca, 2 = média, 3 = boa, 4 = forte
function calcularForca(senha: string): number {
  if (!senha) return 0

  let pontos = 0

  // critério 1: tem pelo menos 8 caracteres
  if (senha.length >= 8) pontos++

  // critério 2: tem letra maiúscula
  if (/[A-Z]/.test(senha)) pontos++

  // critério 3: tem número
  if (/[0-9]/.test(senha)) pontos++

  // critério 4: tem caractere especial
  if (/[^A-Za-z0-9]/.test(senha)) pontos++

  return pontos
}

// Retorna o label baseado na força (0-4)
function obterLabel(forca: number): string {
  switch (forca) {
    case 1: return 'Senha fraca'
    case 2: return 'Senha média'
    case 3: return 'Senha boa'
    case 4: return 'Senha forte'
    default: return ''
  }
}

export function PasswordStrengthIndicator({ senha }: PasswordStrengthIndicatorProps) {
  const forca = calcularForca(senha)
  const label = obterLabel(forca)

  // se senha vazia, não renderiza nada
  if (!senha) return null

  // cria array [1, 2, 3, 4] para renderizar as 4 barras
  const barras = [1, 2, 3, 4]

  return (
    <div className={styles.container}>
      <div className={styles.bars}>
        {barras.map((numero) => (
          <div
            key={numero}
            className={`${styles.bar} ${numero <= forca ? styles.barActive : ''}`}
            data-forca={forca}  /* permite estilizar cor por nível */
          />
        ))}
      </div>

      {forca >= 3 && (
        <div className={styles.label}>
          <Check size={14} />
          <span>{label}</span>
        </div>
      )}

      {forca > 0 && forca < 3 && (
        <span className={styles.labelWeak}>{label}</span>
      )}
    </div>
  )
}