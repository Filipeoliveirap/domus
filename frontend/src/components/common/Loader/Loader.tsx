'use client'

import { clsx } from 'clsx'
import styles from './Loader.module.css'

type Size = 'sm' | 'md' | 'lg'

export type LoaderVariant =
  | 'circular' | 'classic' | 'pulse' | 'pulse-dot' | 'dots' | 'typing'
  | 'wave' | 'bars' | 'terminal' | 'text-blink' | 'text-shimmer' | 'loading-dots'

export interface LoaderProps {
  variant?: LoaderVariant
  size?: Size
  text?: string
  className?: string
}

const CARREGANDO = 'Carregando'

function Sr() {
  return <span className={styles.srOnly}>{CARREGANDO}</span>
}

export function CircularLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.circular, styles[size], className)} role="status">
      <Sr />
    </span>
  )
}

export function ClassicLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  const raio = size === 'sm' ? 8 : size === 'lg' ? 12 : 10
  const larg = size === 'sm' ? 1.5 : size === 'lg' ? 2.5 : 2
  const alt = size === 'sm' ? 6 : size === 'lg' ? 10 : 8
  return (
    <span className={clsx(styles.classic, styles[size], className)} role="status">
      {Array.from({ length: 12 }).map((_, i) => (
        <span
          key={i}
          className={styles.classicBar}
          style={{
            width: `${larg}px`,
            height: `${alt}px`,
            marginLeft: `${-larg / 2}px`,
            transformOrigin: `${larg / 2}px ${raio}px`,
            transform: `rotate(${i * 30}deg)`,
            animationDelay: `${i * 0.1}s`,
          }}
        />
      ))}
      <Sr />
    </span>
  )
}

export function PulseLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.pulse, styles[size], className)} role="status">
      <span className={styles.pulseRing} />
      <Sr />
    </span>
  )
}

export function PulseDotLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.pulseDot, styles[size], className)} role="status">
      <Sr />
    </span>
  )
}

export function DotsLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.dotsRow, styles[size], className)} role="status">
      {[0, 1, 2].map((i) => (
        <span key={i} className={clsx(styles.dot, styles.bounce)} style={{ animationDelay: `${i * 160}ms` }} />
      ))}
      <Sr />
    </span>
  )
}

export function TypingLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.dotsRow, styles.typingRow, styles[size], className)} role="status">
      {[0, 1, 2].map((i) => (
        <span key={i} className={clsx(styles.dot, styles.typing)} style={{ animationDelay: `${i * 250}ms` }} />
      ))}
      <Sr />
    </span>
  )
}

export function WaveLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  const alturas =
    size === 'sm' ? [6, 9, 12, 9, 6] : size === 'lg' ? [10, 15, 20, 15, 10] : [8, 12, 16, 12, 8]
  return (
    <span className={clsx(styles.waveRow, styles[size], className)} role="status">
      {alturas.map((h, i) => (
        <span key={i} className={styles.waveBar} style={{ height: `${h}px`, animationDelay: `${i * 100}ms` }} />
      ))}
      <Sr />
    </span>
  )
}

export function BarsLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.barsRow, styles[size], className)} role="status">
      {[0, 1, 2].map((i) => (
        <span key={i} className={styles.bar} style={{ animationDelay: `${i * 0.2}s` }} />
      ))}
      <Sr />
    </span>
  )
}

export function TerminalLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.terminalRow, styles[size], className)} role="status">
      <span className={styles.terminalPrompt}>{'>'}</span>
      <span className={styles.terminalCursor} />
      <Sr />
    </span>
  )
}

export function TextBlinkLoader({ text = 'Pensando', className, size = 'md' }: { text?: string; className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.textBase, styles.textBlink, styles[size], className)} role="status" aria-live="polite">
      {text}
    </span>
  )
}

export function TextShimmerLoader({ text = 'Pensando', className, size = 'md' }: { text?: string; className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.textBase, styles.textShimmer, styles[size], className)} role="status" aria-live="polite">
      {text}
    </span>
  )
}

export function TextDotsLoader({ text = 'Carregando', className, size = 'md' }: { text?: string; className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.loadingDots, styles[size], className)} role="status" aria-live="polite">
      <span className={clsx(styles.textBase, styles[size], styles.txt)}>{text}</span>
      <span className={styles.pts}>
        <span>.</span><span>.</span><span>.</span>
      </span>
    </span>
  )
}

export function Loader({ variant = 'circular', size = 'md', text, className }: LoaderProps) {
  switch (variant) {
    case 'classic': return <ClassicLoader size={size} className={className} />
    case 'pulse': return <PulseLoader size={size} className={className} />
    case 'pulse-dot': return <PulseDotLoader size={size} className={className} />
    case 'dots': return <DotsLoader size={size} className={className} />
    case 'typing': return <TypingLoader size={size} className={className} />
    case 'wave': return <WaveLoader size={size} className={className} />
    case 'bars': return <BarsLoader size={size} className={className} />
    case 'terminal': return <TerminalLoader size={size} className={className} />
    case 'text-blink': return <TextBlinkLoader text={text} size={size} className={className} />
    case 'text-shimmer': return <TextShimmerLoader text={text} size={size} className={className} />
    case 'loading-dots': return <TextDotsLoader text={text} size={size} className={className} />
    case 'circular':
    default: return <CircularLoader size={size} className={className} />
  }
}
