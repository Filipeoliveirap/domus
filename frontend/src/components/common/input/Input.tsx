import React, { forwardRef } from 'react'
import styles from './Input.module.css'


interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string      
  label?: string        
  error?: string       
  leftIcon?: React.ReactNode   
  rightElement?: React.ReactNode  
  labelRight?: React.ReactNode
}


export const Input = forwardRef<HTMLInputElement, InputProps>(
  function Input(
    { id, label, error, leftIcon, rightElement, labelRight, className, ...props },
    ref  
  ) {
    return (
      <div className={styles.field}>

        {(label || labelRight) && (
          <div className={styles.labelRow}>
            {label && (
              <label className={styles.label} htmlFor={id}>
                {label}
              </label>
            )}
            {labelRight}
          </div>
        )}
        <div className={styles.inputWrapper}>

          {leftIcon && (
            <span className={styles.leftIcon} aria-hidden="true">
              {leftIcon}
            </span>
          )}

          <input
            ref={ref} 
            id={id}
            className={[
              styles.input,
              leftIcon ? styles.withLeftIcon : '',
              rightElement ? styles.withRightElement : '',
              error ? styles.inputError : '',
              className ?? '',
            ].join(' ')}
            {...props}
          />

          {rightElement && (
            <span className={styles.rightElement}>
              {rightElement}
            </span>
          )}
        </div>
        {error && (
          <span className={styles.fieldError} role="alert">
            {error}
          </span>
        )}

      </div>
    )
  }
)

Input.displayName = 'Input'