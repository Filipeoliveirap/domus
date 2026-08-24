'use client'

import { useState, useCallback, useEffect, useRef } from 'react'
import Cropper from 'react-easy-crop'
import type { Area, Point } from 'react-easy-crop'
import { Check, X, ZoomIn, RotateCcw } from 'lucide-react'
import { notificar } from '@/components/common/Notificacao/notificar'
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

function createImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.addEventListener('load', () => resolve(img))
    img.addEventListener('error', (e) =>
      reject(e instanceof ErrorEvent ? e.error ?? new Error(e.message) : new Error('Falha ao carregar imagem')),
    )
    img.src = url
  })
}

async function getCroppedImg(imageUrl: string, pixelCrop: Area, formato: Props['formato']): Promise<File> {
  if (pixelCrop.width <= 0 || pixelCrop.height <= 0) {
    throw new Error('Área de recorte inválida.')
  }

  const img = await createImage(imageUrl)
  // decode() garante que os pixels estão prontos antes do drawImage.
  // Sem isto, drawImage pode desenhar nada → canvas preto (JPEG não tem alpha).
  try { await img.decode() } catch { /* fallback: já carregou */ }

  const { largura, altura } = SAIDA[formato]
  const canvas = document.createElement('canvas')
  canvas.width = largura
  canvas.height = altura
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas não suportado.')

  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(
    img,
    pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
    0, 0, largura, altura,
  )

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('Falha ao gerar imagem recortada.'))
          return
        }
        const name = imageUrl.replace(/^.*[\\/]/, '').replace(/\.\w+$/, '.jpg') || 'foto.jpg'
        resolve(new File([blob], name, { type: 'image/jpeg' }))
      },
      'image/jpeg',
      0.92,
    )
  })
}

/** Desenha só a porção recortada da imagem dentro do canvas de preview.
 *  Diferente da versão com `<img>` + objectPosition/transform, isto bate 1:1 com
 *  o que vai sair no cropper — a única diferença é a escala final (preview em vez de 480px). */
async function drawPreviewNoCanvas(
  imageUrl: string,
  pixelCrop: Area,
  canvas: HTMLCanvasElement,
  tamanhoSaida: number,
): Promise<void> {
  if (pixelCrop.width <= 0 || pixelCrop.height <= 0) return

  const img = await createImage(imageUrl)
  try { await img.decode() } catch { /* fallback */ }

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  canvas.width = tamanhoSaida
  canvas.height = tamanhoSaida
  ctx.imageSmoothingQuality = 'high'
  ctx.clearRect(0, 0, tamanhoSaida, tamanhoSaida)
  ctx.drawImage(
    img,
    pixelCrop.x, pixelCrop.y, pixelCrop.width, pixelCrop.height,
    0, 0, tamanhoSaida, tamanhoSaida,
  )
}

export function CropperFoto({ arquivo, formato, onCancelar, onConfirmar }: Props) {
  const [crop, setCrop] = useState<Point>({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<Area | null>(null)
  const [gerando, setGerando] = useState(false)
  const [mediaErro, setMediaErro] = useState(false)
  const [urlBlob, setUrlBlob] = useState<string | null>(null)
  const [urlBase64, setUrlBase64] = useState<string | null>(null)
  const canvasPreviewRef = useRef<HTMLCanvasElement | null>(null)
  const timeoutErroRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const fallbackTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Tenta blob URL primeiro (mais rápido). Se o Cropper não sinalizar
  // `onMediaLoaded` em 2s, tenta base64 (mais lento mas mais confiável em
  // Safari mobile — blob URLs podem falhar silenciosamente em alguns casos).
  useEffect(() => {
    let url: string | null = null
    try {
      url = URL.createObjectURL(arquivo)
      setUrlBlob(url)
    } catch {
      // Falha ao criar blob URL — tenta base64 imediatamente
      converterParaBase64(arquivo)
      return
    }

    return () => {
      if (url) URL.revokeObjectURL(url)
      if (fallbackTimeoutRef.current) {
        clearTimeout(fallbackTimeoutRef.current)
        fallbackTimeoutRef.current = null
      }
    }
  }, [arquivo])

  function converterParaBase64(file: File) {
    const reader = new FileReader()
    reader.onload = () => {
      setUrlBase64(reader.result as string)
      // Marca como carregada para o timeout de erro não disparar
      if (timeoutErroRef.current) {
        clearTimeout(timeoutErroRef.current)
        timeoutErroRef.current = null
      }
    }
    reader.onerror = () => {
      setMediaErro(true)
      notificar.erro('Não foi possível carregar a foto', 'Tente outra imagem.')
    }
    reader.readAsDataURL(file)
  }

  // Timeout de segurança: se o Cropper não sinalizar `onMediaLoaded` em 8s,
  // assume que a imagem não carregou (problema conhecido no Safari mobile com
  // alguns blob URLs) e mostra mensagem de erro. Sem isso, o usuário vê o modal
  // vazio sem feedback nenhum.
  useEffect(() => {
    if (!urlBlob || mediaErro) return
    timeoutErroRef.current = setTimeout(() => {
      setMediaErro(true)
    }, 8000)
    return () => {
      if (timeoutErroRef.current) {
        clearTimeout(timeoutErroRef.current)
        timeoutErroRef.current = null
      }
    }
  }, [urlBlob, mediaErro])

  // Fallback: se o blob URL foi criado mas o Cropper não sinalizou
  // `onMediaLoaded` em 2s, converte para base64 como backup.
  useEffect(() => {
    if (!urlBlob || mediaErro || urlBase64) return
    fallbackTimeoutRef.current = setTimeout(() => {
      converterParaBase64(arquivo)
    }, 2000)
    return () => {
      if (fallbackTimeoutRef.current) {
        clearTimeout(fallbackTimeoutRef.current)
        fallbackTimeoutRef.current = null
      }
    }
  }, [urlBlob, mediaErro, urlBase64, arquivo])

  const urlParaUsar = urlBase64 || urlBlob

  const aspect = formato === 'circulo' ? 1 : 3 / 1

  const onCropComplete = useCallback((_: Area, croppedAreaPixels: Area) => {
    // O react-easy-crop dispara este callback antes da imagem terminar de carregar,
    // com width/height zerados. Descartamos esses valores inválidos — o próximo
    // disparo (já com a imagem carregada) trará os valores reais.
    if (croppedAreaPixels.width > 0 && croppedAreaPixels.height > 0) {
      setCroppedAreaPixels(croppedAreaPixels)
    }
  }, [])

  // Redesenha o preview no canvas sempre que a área recortada muda.
  // O canvas é a fonte da verdade visual — bate 1:1 com o que vai sair no crop final.
  useEffect(() => {
    if (!urlParaUsar || !canvasPreviewRef.current) return
    if (!croppedAreaPixels || croppedAreaPixels.width <= 0 || croppedAreaPixels.height <= 0) return
    const canvas = canvasPreviewRef.current
    drawPreviewNoCanvas(urlParaUsar, croppedAreaPixels, canvas, SAIDA.circulo.largura).catch(() => {
      // Falha silenciosa — o usuário ainda vê o Cropper, só não vê o preview.
    })
  }, [urlParaUsar, croppedAreaPixels])

  async function confirmar() {
    if (!croppedAreaPixels) return
    if (!urlParaUsar) {
      notificar.erro('Foto não disponível', 'Selecione a foto novamente.')
      return
    }
    setGerando(true)
    try {
      const recortado = await getCroppedImg(urlParaUsar, croppedAreaPixels, formato)
      onConfirmar(recortado)
    } catch (err) {
      setGerando(false)
      const mensagem = err instanceof Error ? err.message : 'Erro desconhecido.'
      notificar.erro('Não foi possível processar a foto', mensagem)
    }
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
            {/* Renderiza o Cropper sempre que temos uma URL válida (blob ou base64).
                O timeout de segurança detecta se a imagem demora demais e mostra erro. */}
            {urlParaUsar && !mediaErro && (
              <Cropper
                image={urlParaUsar}
                crop={crop}
                zoom={zoom}
                aspect={aspect}
                onCropChange={setCrop}
                onZoomChange={setZoom}
                onCropComplete={onCropComplete}
                cropShape={formato === 'circulo' ? 'round' : 'rect'}
                showGrid
                objectFit="cover"
                onMediaLoaded={() => {
                  // Imagem carregou — cancela o timeout de erro.
                  if (timeoutErroRef.current) {
                    clearTimeout(timeoutErroRef.current)
                    timeoutErroRef.current = null
                  }
                }}
                classes={{
                  containerClassName: styles.cropperContainer,
                  mediaClassName: styles.cropperMedia,
                  cropAreaClassName: styles.cropperArea,
                }}
              />
            )}
            {mediaErro && (
              <div className={styles.loadingOverlay}>
                <span className={styles.loadingText}>Esta foto não pôde ser carregada.</span>
                <button type="button" className={styles.btnCancelar} onClick={onCancelar}>
                  Fechar
                </button>
              </div>
            )}
            {gerando && (
              <div className={styles.loadingOverlay}>
                <div className={styles.spinner} />
                <span className={styles.loadingText}>Processando foto…</span>
              </div>
            )}
          </div>

          {formato === 'circulo' && croppedAreaPixels && croppedAreaPixels.width > 0 && croppedAreaPixels.height > 0 && urlParaUsar && (
            <div className={styles.previewCol}>
              <span className={styles.previewLabel}>Como vai ficar</span>
              <div className={styles.previewCircle}>
                <canvas ref={canvasPreviewRef} className={styles.previewCanvas} />
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
