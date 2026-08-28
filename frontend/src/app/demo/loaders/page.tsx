'use client'

import { useRef, useState } from 'react'
import { notFound } from 'next/navigation'
import Link, { useLinkStatus } from 'next/link'
import { Home } from 'lucide-react'
import { Loader, type LoaderVariant } from '@/components/common/Loader/Loader'
import { BarraProgresso } from '@/components/layout/NavProgress/BarraProgresso'
import { OverlayCarregando } from '@/components/common/OverlayCarregando/OverlayCarregando'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { ItemAnimado } from '@/components/common/Transicao/ItemAnimado'
import { useListaComSaida } from '@/hooks/useListaComSaida'
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
  const [blocoVisivel, setBlocoVisivel] = useState(true)
  const [itens, setItens] = useState<{ id: number; texto: string }[]>([
    { id: 1, texto: 'Primeiro item' },
    { id: 2, texto: 'Segundo item' },
  ])
  const proximoId = useRef(3)
  const lista = useListaComSaida(itens, (i) => String(i.id))

  function addItem() {
    setItens((s) => [...s, { id: proximoId.current, texto: `Item ${proximoId.current++}` }])
  }
  function removeItem(id: number) {
    setItens((s) => s.filter((i) => i.id !== id))
  }

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

      <section className={styles.secao}>
        <h2 className={styles.secaoTitulo}>Transições (peça D)</h2>
        <p className={styles.aviso} style={{ marginBottom: '0.5rem' }}>
          O conteúdo de rota/abas já entra animado no app todo. Aqui: bloco fora de rota e lista com entrada/saída.
        </p>
        <div className={styles.simulador}>
          <button onClick={() => setBlocoVisivel((v) => !v)}>
            {blocoVisivel ? 'Esconder bloco' : 'Mostrar bloco'}
          </button>
          <button onClick={addItem}>Adicionar item</button>
        </div>
        {blocoVisivel && (
          <Transicao modo="subir">
            <div className={styles.celula} style={{ marginTop: '0.75rem', minHeight: 80 }}>
              Bloco que entra com <code>&lt;Transicao modo=&quot;subir&quot;&gt;</code>
            </div>
          </Transicao>
        )}
        <div className={styles.linkSimLista} style={{ marginTop: '0.75rem', maxWidth: 320 }}>
          {lista.map(({ item, chave, saindo }) => (
            <ItemAnimado key={chave} saindo={saindo}>
              <div className={styles.linkSim} style={{ justifyContent: 'space-between' }}>
                <span>{item.texto}</span>
                <button onClick={() => removeItem(item.id)} disabled={saindo} aria-label="Remover">✕</button>
              </div>
            </ItemAnimado>
          ))}
        </div>
      </section>

      <BarraProgresso ativo={simBarra} />
      <OverlayCarregando ativo={simOverlay} texto="Processando…" />
    </div>
  )
}
