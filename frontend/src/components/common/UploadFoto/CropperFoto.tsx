'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { Check, X, ZoomIn } from 'lucide-react'
import styles from './CropperFoto.module.css'

interface Medidas {
  /** Tamanho renderizado do viewport (o quadrado/retângulo de recorte), em px de tela. */
  cw: number
  ch: number
  /** Tamanho natural do arquivo escolhido. */
  iw: number
  ih: number
  /** Escala mínima que já cobre o viewport inteiro (equivalente a `background-size: cover`). */
  baseScale: number
}

interface Props {
  arquivo: File
  /** Círculo é 1:1 (foto de pessoa, logo); banner é 3:1 (ex.: capa de evento). */
  formato: 'circulo' | 'banner'
  onCancelar: () => void
  onConfirmar: (recortado: File) => void
}

// Tamanho de saída fixo: não depende do quanto a tela do usuário rendereizou o viewport,
// então o arquivo final tem sempre a mesma resolução (boa o bastante para exibir em
// qualquer tamanho de avatar/banner do sistema, sem pesar demais o upload).
const SAIDA: Record<Props['formato'], { largura: number; altura: number }> = {
  circulo: { largura: 480, altura: 480 },
  banner: { largura: 1200, altura: 400 },
}

const ZOOM_MAX = 3

function clamp(valor: number, min: number, max: number) {
  return Math.min(max, Math.max(min, valor))
}

/**
 * Recorte por arrastar-e-ampliar sobre canvas — sem dependência nova.
 *
 * <p>Cogitamos uma lib pronta (ex. `react-easy-crop`), mas o projeto mantém a lista de
 * dependências deliberadamente pequena e o recorte aqui é simples o bastante (uma forma,
 * sem rotação) para não justificar mais um pacote. A matemática é a mesma de um
 * `background-size: cover` manual: a imagem entra cobrindo o viewport (`baseScale`), o
 * slider multiplica esse tanto (zoom ≥ 1) e o arrasto é sempre limitado para nunca abrir
 * uma borda vazia dentro do viewport.
 */
export function CropperFoto({ arquivo, formato, onCancelar, onConfirmar }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const imgRef = useRef<HTMLImageElement>(null)
  const arrastoRef = useRef<{ inicioX: number; inicioY: number; posInicial: { x: number; y: number } } | null>(null)

  const [medidas, setMedidas] = useState<Medidas | null>(null)
  const [zoom, setZoom] = useState(1)
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const [arrastando, setArrastando] = useState(false)
  const [gerando, setGerando] = useState(false)

  const objectUrl = useMemo(() => URL.createObjectURL(arquivo), [arquivo])
  useEffect(() => () => URL.revokeObjectURL(objectUrl), [objectUrl])

  function limitesPara(m: Medidas, z: number) {
    const dw = m.iw * m.baseScale * z
    const dh = m.ih * m.baseScale * z
    return {
      maxX: Math.max(0, (dw - m.cw) / 2),
      maxY: Math.max(0, (dh - m.ch) / 2),
    }
  }

  function aoCarregarImagem() {
    const container = containerRef.current
    const img = imgRef.current
    if (!container || !img) return

    const rect = container.getBoundingClientRect()
    const iw = img.naturalWidth
    const ih = img.naturalHeight
    const baseScale = Math.max(rect.width / iw, rect.height / ih)

    setMedidas({ cw: rect.width, ch: rect.height, iw, ih, baseScale })
    setPos({ x: 0, y: 0 })
    setZoom(1)
  }

  function aoMudarZoom(novoZoom: number) {
    setZoom(novoZoom)
    if (!medidas) return
    const { maxX, maxY } = limitesPara(medidas, novoZoom)
    setPos((p) => ({ x: clamp(p.x, -maxX, maxX), y: clamp(p.y, -maxY, maxY) }))
  }

  function aoIniciarArrasto(clientX: number, clientY: number) {
    arrastoRef.current = { inicioX: clientX, inicioY: clientY, posInicial: pos }
    setArrastando(true)
  }

  function aoMoverArrasto(clientX: number, clientY: number) {
    const inicio = arrastoRef.current
    if (!inicio || !medidas) return
    const { maxX, maxY } = limitesPara(medidas, zoom)
    const novoX = clamp(inicio.posInicial.x + (clientX - inicio.inicioX), -maxX, maxX)
    const novoY = clamp(inicio.posInicial.y + (clientY - inicio.inicioY), -maxY, maxY)
    setPos({ x: novoX, y: novoY })
  }

  function aoTerminarArrasto() {
    arrastoRef.current = null
    setArrastando(false)
  }

  async function confirmar() {
    const img = imgRef.current
    if (!img || !medidas) return
    setGerando(true)

    const { largura, altura } = SAIDA[formato]
    const displayedScale = medidas.baseScale * zoom
    const dw = medidas.iw * displayedScale
    const dh = medidas.ih * displayedScale
    const imgLeft = (medidas.cw - dw) / 2 + pos.x
    const imgTop = (medidas.ch - dh) / 2 + pos.y

    const sx = -imgLeft / displayedScale
    const sy = -imgTop / displayedScale
    const sw = medidas.cw / displayedScale
    const sh = medidas.ch / displayedScale

    const canvas = document.createElement('canvas')
    canvas.width = largura
    canvas.height = altura
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      setGerando(false)
      return
    }
    ctx.drawImage(img, sx, sy, sw, sh, 0, 0, largura, altura)

    canvas.toBlob(
      (blob) => {
        setGerando(false)
        if (!blob) return
        const recortado = new File([blob], arquivo.name.replace(/\.\w+$/, '.jpg'), {
          type: 'image/jpeg',
        })
        onConfirmar(recortado)
      },
      'image/jpeg',
      0.92,
    )
  }

  return (
    <div className={styles.overlay} onMouseDown={onCancelar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label="Ajustar recorte da foto"
      >
        <div className={styles.cabecalho}>
          <span className={styles.titulo}>Ajustar recorte</span>
          <button type="button" className={styles.fechar} onClick={onCancelar} aria-label="Cancelar">
            <X size={18} />
          </button>
        </div>

        <div
          ref={containerRef}
          className={`${styles.viewport} ${formato === 'circulo' ? styles.viewportCirculo : styles.viewportBanner}`}
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture(e.pointerId)
            aoIniciarArrasto(e.clientX, e.clientY)
          }}
          onPointerMove={(e) => arrastando && aoMoverArrasto(e.clientX, e.clientY)}
          onPointerUp={aoTerminarArrasto}
          onPointerCancel={aoTerminarArrasto}
        >
          {/* eslint-disable-next-line @next/next/no-img-element -- é uma prévia local de blob, não uma imagem servida pela API */}
          <img
            ref={imgRef}
            src={objectUrl}
            alt=""
            className={styles.imagem}
            draggable={false}
            onLoad={aoCarregarImagem}
            style={
              medidas
                ? {
                    width: medidas.iw * medidas.baseScale * zoom,
                    height: medidas.ih * medidas.baseScale * zoom,
                    transform: `translate(${pos.x}px, ${pos.y}px)`,
                  }
                : undefined
            }
          />
        </div>

        <div className={styles.controles}>
          <ZoomIn size={16} className={styles.zoomIcone} aria-hidden="true" />
          <input
            type="range"
            min={1}
            max={ZOOM_MAX}
            step={0.01}
            value={zoom}
            onChange={(e) => aoMudarZoom(Number(e.target.value))}
            className={styles.zoomSlider}
            aria-label="Zoom do recorte"
            disabled={!medidas}
          />
        </div>

        <div className={styles.rodape}>
          <button type="button" className={styles.btnCancelar} onClick={onCancelar} disabled={gerando}>
            Cancelar
          </button>
          <button
            type="button"
            className={styles.btnConfirmar}
            onClick={confirmar}
            disabled={!medidas || gerando}
          >
            <Check size={16} aria-hidden="true" />
            {gerando ? 'Aplicando…' : 'Aplicar recorte'}
          </button>
        </div>
      </div>
    </div>
  )
}
