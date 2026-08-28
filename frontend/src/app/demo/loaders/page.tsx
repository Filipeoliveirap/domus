'use client'

import { useState } from 'react'
import { notFound } from 'next/navigation'
import Link, { useLinkStatus } from 'next/link'
import { Home } from 'lucide-react'
import { Loader, type LoaderVariant } from '@/components/common/Loader/Loader'
import { BarraProgresso } from '@/components/layout/NavProgress/BarraProgresso'
import { OverlayCarregando } from '@/components/common/OverlayCarregando/OverlayCarregando'
import styles from './page.module.css'

/** Reproduz o comportamento do modo 'barra-e-link' do sidebar: o ícone vira spinner
 *  enquanto a navegação disparada por este link está pendente. */
function LinkSimulado({ href, texto }: { href: string; texto: string }) {
  return (
    <Link href={href} className={styles.linkSim}>
      <IconeLinkSim />
      <span>{texto}</span>
    </Link>
  )
}

function IconeLinkSim() {
  const { pending } = useLinkStatus()
  return pending ? <Loader variant="circular" size="sm" /> : <Home size={20} />
}

const VARIANTES: LoaderVariant[] = [
  'circular', 'classic', 'pulse', 'pulse-dot', 'dots', 'typing',
  'wave', 'bars', 'terminal', 'text-blink', 'text-shimmer', 'loading-dots',
]

const TAMANHOS = ['sm', 'md', 'lg'] as const

export default function DemoLoadersPage() {
  if (process.env.NODE_ENV === 'production') notFound()

  const [tamanho, setTamanho] = useState<(typeof TAMANHOS)[number]>('md')
  const [simBarra, setSimBarra] = useState(false)
  const [simOverlay, setSimOverlay] = useState(false)

  function dispararBarra() {
    setSimBarra(true)
    setTimeout(() => setSimBarra(false), 2000)
  }
  function dispararOverlay() {
    setSimOverlay(true)
    setTimeout(() => setSimOverlay(false), 2000)
  }

  return (
    <div className={styles.wrapper}>
      <h1 className={styles.titulo}>Loaders</h1>
      <p className={styles.aviso}>Rota de teste (dev-only). Escolha quais variantes ficam.</p>

      <div className={styles.toggle}>
        {TAMANHOS.map((t) => (
          <button key={t} data-ativo={tamanho === t} onClick={() => setTamanho(t)}>
            {t}
          </button>
        ))}
      </div>

      <div className={styles.grid}>
        {VARIANTES.map((v) => (
          <div key={v} className={styles.celula}>
            <Loader variant={v} size={tamanho} text="Carregando" />
            <span className={styles.nome}>{v}</span>
          </div>
        ))}
      </div>

      <section className={styles.secao}>
        <h2 className={styles.secaoTitulo}>Navegação de rota (não bloqueia)</h2>
        <div className={styles.simulador}>
          <button onClick={dispararBarra}>Simular barra (2s)</button>
        </div>
        <p className={styles.aviso} style={{ marginTop: '1rem', marginBottom: '0.5rem' }}>
          Modo &quot;barra-e-link&quot;: clique — o ícone vira spinner enquanto a rota carrega (navegação real).
        </p>
        <div className={styles.linkSimLista}>
          <LinkSimulado href="/demo/loaders?n=1" texto="Navegar (?n=1)" />
          <LinkSimulado href="/demo/loaders?n=2" texto="Navegar (?n=2)" />
          <LinkSimulado href="/inicio" texto="Ir pra /inicio" />
        </div>
      </section>

      <section className={styles.secao}>
        <h2 className={styles.secaoTitulo}>Operação bloqueante (overlay)</h2>
        <p className={styles.aviso} style={{ marginBottom: '0.5rem' }}>
          Pra pagar, excluir, submeter cadastro — segura a tela e bloqueia clique por baixo.
        </p>
        <div className={styles.simulador}>
          <button onClick={dispararOverlay}>Simular overlay (2s)</button>
        </div>
      </section>

      <BarraProgresso ativo={simBarra} />
      <OverlayCarregando ativo={simOverlay} texto="Processando…" />
    </div>
  )
}
