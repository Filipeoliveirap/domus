import React, { forwardRef } from 'react'
import { ChevronDown } from 'lucide-react'
import styles from './Select.module.css'

interface SelectOption {
  value: string
  label: string
}

interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  id: string
  label?: string
  error?: string
  placeholder?: string
  options: SelectOption[]
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  function Select(
    { id, label, error, placeholder, options, className, ...props },
    ref,
  ) {
    return (
      <div className={styles.field}>
        {label && (
          <label className={styles.label} htmlFor={id}>
            {label}
          </label>
        )}
        <div className={styles.selectWrapper}>
          <select
            ref={ref}
            id={id}
            className={[
              styles.select,
              error ? styles.selectError : '',
              className ?? '',
            ].join(' ')}
            {...props}
          >
            {placeholder && <option value="">{placeholder}</option>}
            {options.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
          <span className={styles.chevron} aria-hidden="true">
            <ChevronDown size={18} />
          </span>
        </div>
        {error && (
          <span className={styles.fieldError} role="alert">
            {error}
          </span>
        )}
      </div>
    )
  },
)

Select.displayName = 'Select'