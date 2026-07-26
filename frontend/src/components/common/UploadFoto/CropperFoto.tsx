'use client'

import { useState, useCallback, useRef, useEffect } from 'react'
import Cropper from 'react-easy-crop'
import type { Area, Point } from 'react-easy-crop'
import { Check, X, ZoomIn, RotateCcw } from 'lucide-react'
import styles from './CropperFoto.module.css'

interface Props {
  arquivo: File
  /** Círculo é 1:1 (foto de pessoa, logo); banner é 3:1 (ex.: capa de evento). */
  formato: 'circulo' | 'banner'
  onCancelar: () => void
  onConfirmar: (recortado: File) => void
}

const SAIDA: Record<Props['formato'], { largura: number; altura: number }> = {
  circulo: { largura: 480, altura: 480 },
  banner: { largura: 1200, altura: 400 },
}

function getCroppedImg(imageUrl: string, pixelCrop: Area, formato: Props['formato']): Promise<File> {
  const canvas = document.createElement('canvas')
  const { largura, altura } = SAIDA[formato]
  canvas.width = largura
  canvas.height = altura
  const ctx = canvas.getContext('2d')
  if (!ctx) return Promise.reject(new Error('Canvas not supported'))

  const img = new Image()
  img.src = imageUrl

  return new Promise((resolve) => {
    img.onload = () => {
      ctx.drawImage(
        img,
        pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
        0, 0, largura, altura,
      )
      canvas.toBlob(
        (blob) => {
          if (!blob) return
          const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.jpg') || 'foto.jpg'
          resolve(new File([blob], name, { type: 'image/jpeg' }))
        },
        'image/jpeg',
        0.92,
      )
    }
  })
}

export function CropperFoto({ arquivo, formato, onCancelar, onConfirmar }: Props) {
  const [crop, setCrop] = useState<Point>({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<Area | null>(null)
  const [gerando, setGerando] = useState(false)
  const urlRef = useRef(URL.createObjectURL(arquivo))

  useEffect(() => () => URL.revokeObjectURL(urlRef.current), [])

  const aspect = formato === 'circulo' ? 1 : 3 / 1

  const onCropComplete = useCallback((_: Area, croppedAreaPixels: Area) => {
    setCroppedAreaPixels(croppedAreaPixels)
  }, [])

  async function confirmar() {
    if (!croppedAreaPixels) return
    setGerando(true)
    const recortado = await getCroppedImg(urlRef.current, croppedAreaPixels, formato)
    onConfirmar(recortado)
  }

  const zoomPercent = Math.round(zoom * 100)

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

        <div className={styles.corpo}>
          <div className={styles.viewportWrap}>
            <Cropper
              image={urlRef.current}
              crop={crop}
              zoom={zoom}
              aspect={aspect}
              onCropChange={setCrop}
              onZoomChange={setZoom}
              onCropComplete={onCropComplete}
              cropShape={formato === 'circulo' ? 'round' : 'rect'}
              showGrid
              objectFit="cover"
              classes={{
                containerClassName: styles.cropperContainer,
                mediaClassName: styles.cropperMedia,
                cropAreaClassName: styles.cropperArea,
              }}
            />
            {gerando && (
              <div className={styles.loadingOverlay}>
                <div className={styles.spinner} />
                <span className={styles.loadingText}>Processando foto…</span>
              </div>
            )}
          </div>

          {formato === 'circulo' && croppedAreaPixels && (
            <div className={styles.previewCol}>
              <span className={styles.previewLabel}>Como vai ficar</span>
              <div className={styles.previewCircle}>
                <img
                  src={urlRef.current}
                  alt="Preview"
                  className={styles.previewImg}
                  style={{
                    objectPosition: `${-croppedAreaPixels.x}px ${-croppedAreaPixels.y}px`,
                    width: croppedAreaPixels.width,
                    height: croppedAreaPixels.height,
                    transform: `scale(${SAIDA.circulo.largura / croppedAreaPixels.width})`,
                    transformOrigin: 'top left',
                  }}
                />
              </div>
            </div>
          )}
        </div>

        <div className={styles.controles}>
          <button
            type="button"
            className={styles.btnReset}
            onClick={() => { setCrop({ x: 0, y: 0 }); setZoom(1) }}
            title="Centralizar"
            aria-label="Centralizar imagem"
          >
            <RotateCcw size={16} />
          </button>
          <ZoomIn size={16} className={styles.zoomIcone} aria-hidden="true" />
          <input
            type="range"
            min={1}
            max={3}
            step={0.01}
            value={zoom}
            onChange={(e) => setZoom(Number(e.target.value))}
            className={styles.zoomSlider}
            aria-label="Zoom do recorte"
          />
          <span className={styles.zoomValor}>{zoomPercent}%</span>
        </div>

        <div className={styles.rodape}>
          <button type="button" className={styles.btnCancelar} onClick={onCancelar} disabled={gerando}>
            Cancelar
          </button>
          <button
            type="button"
            className={styles.btnConfirmar}
            onClick={confirmar}
            disabled={!croppedAreaPixels || gerando}
          >
            <Check size={16} aria-hidden="true" />
            {gerando ? 'Aplicando…' : 'Aplicar recorte'}
          </button>
        </div>
      </div>
    </div>
  )
}
