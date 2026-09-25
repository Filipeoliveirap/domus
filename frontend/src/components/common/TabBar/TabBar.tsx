'use client'

import { clsx } from 'clsx'
import styles from './TabBar.module.css'

interface Tab<T extends string> {
  id: T
  rotulo: string
}

interface Props<T extends string> {
  tabs: Tab<T>[]
  activeId: T
  onChange: (id: T) => void
}

export function TabBar<T extends string>({ tabs, activeId, onChange }: Props<T>) {
  return (
    <div className={styles.tabs} role="tablist" aria-label="Filtro de visualização">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          role="tab"
          aria-selected={activeId === tab.id}
          className={clsx(styles.tab, activeId === tab.id && styles.active)}
          onClick={() => onChange(tab.id)}
        >
          {tab.rotulo}
        </button>
      ))}
    </div>
  )
}
