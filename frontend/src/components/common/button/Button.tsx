import React  from "react";
import styles from "./Button.module.css";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    variant? : 'primary' | 'ghost' | 'secondary' | 'danger'
    size? : 'sm' | 'md' | 'lg'
    isLoading? : boolean
    disabled? : boolean 
    loadingText?: string;
    children: React.ReactNode
}

export function Button({
    variant = 'primary',
    size = 'md',
    isLoading = false,
    disabled = false,
    loadingText,
    children,
    className,
    ...props
}: ButtonProps) {
    const estaDesabilitado = disabled || isLoading;
    return (
        <button
            className={[
                styles.button,
                styles[variant],
                styles[size],
                estaDesabilitado ? styles.disabled : '',
                className ?? '',
            ].join(' ')}
            disabled={estaDesabilitado}
            {...props}
        >
            {isLoading ? (
                <span className={styles.loadingContainer}>
                    <span className={styles.spinner} />
                    {loadingText ?? children} 
                </span>
            ) : (
                children
            )}
        </button>
    )
}