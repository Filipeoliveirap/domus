import React  from "react";
import styles from "./Button.module.css";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    variant? : 'primary' | 'ghost' | 'secondary' | 'danger'
    size? : 'sm' | 'md' | 'lg'
    isLoading? : boolean
    children: React.ReactNode
}

export function Button({
    variant = 'primary',
    size = 'md',
    isLoading = false,
    disabled,
    children,
    className,
    ...props
}: ButtonProps) {
    return (
        <button
            className={[
                styles.button,
                styles[variant],
                styles[size],
                isLoading ? styles.loading : '',
                className ?? '',
            ].join(' ')}
            disabled={disabled || isLoading}
            {...props}
        >
            {/* quando isLoading, mostra um spinner + texto diferente */}
            {isLoading ? (
                <span className={styles.loadingContent}>
                    <span className={styles.spinner} aria-hidden="true"></span>
                    {children}
                </span>
            ): children}
        </button>
    )
}