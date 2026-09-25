'use client'

import { forwardRef, useImperativeHandle, useRef, useState } from 'react'
import { Pencil, X } from 'lucide-react'
import { CropperFoto } from './CropperFoto'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { notificar } from '@/components/common/Notificacao/notificar'
import { urlFoto } from '@/lib/urlFoto'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import styles from './SeletorEPreviaFoto.module.css'

export interface SeletorEPreviaFotoRef {
  abrirSeletor: () => void
}

interface Props {
  fotoId: string | null
  onChange: (fotoId: string | null) => void
  formato?: 'post' | 'banner' | 'circulo'
  disabled?: boolean
}

export const SeletorEPreviaFoto = forwardRef<SeletorEPreviaFotoRef, Props>(
  function SeletorEPreviaFoto({ fotoId, onChange, formato = 'post', disabled = false }, ref) {
    const fileInputRef = useRef<HTMLInputElement>(null)
    const [carregandoFoto, setCarregandoFoto] = useState(false)
    const [arquivoParaRecorte, setArquivoParaRecorte] = useState<File | null>(null)
    const [arquivoOriginal, setArquivoOriginal] = useState<File | null>(null)

    useImperativeHandle(ref, () => ({
      abrirSeletor: () => fileInputRef.current?.click(),
    }))

    const handleSelecionarArquivo = (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return
      setArquivoOriginal(file)
      setArquivoParaRecorte(file)
      e.target.value = ''
    }

    const handleConfirmarRecorte = async (arquivoRecortado: File) => {
      setArquivoParaRecorte(null)
      try {
        setCarregandoFoto(true)
        const formData = new FormData()
        formData.append('arquivo', arquivoRecortado)

        const res = await api.post<{ id: string }>(Endpoints.fotos.UPLOAD, formData, {
          headers: { 'Content-Type': undefined },
        })
        onChange(res.data.id)
      } catch {
        notificar.erro('Não foi possível enviar a foto', 'Tente enviar a imagem novamente.')
      } finally {
        setCarregandoFoto(false)
      }
    }

    const urlPreview = urlFoto(fotoId, 'DISPLAY')

    return (
      <>
        <input
          type="file"
          ref={fileInputRef}
          onChange={handleSelecionarArquivo}
          accept="image/*"
          style={{ display: 'none' }}
          disabled={disabled || carregandoFoto}
        />

        {carregandoFoto && (
          <div className={styles.previewContainerLoading}>
            <Skeleton style={{ width: '100%', height: 200, borderRadius: 14 }} />
            <div className={styles.overlayLoadingFoto}>
              <div className={styles.spinnerFoto} />
              <span>Enviando foto...</span>
            </div>
          </div>
        )}

        {fotoId && urlPreview && !carregandoFoto && (
          <div className={styles.previewContainer}>
            <button
              type="button"
              className={styles.btnEditarFoto}
              onClick={() => {
                if (arquivoOriginal) {
                  setArquivoParaRecorte(arquivoOriginal)
                } else {
                  fileInputRef.current?.click()
                }
              }}
              disabled={disabled}
            >
              <Pencil size={13} />
              Editar / Trocar
            </button>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={urlPreview}
              alt="Prévia da foto"
              className={styles.previewImagem}
              loading="lazy"
            />
            <button
              type="button"
              className={styles.btnRemoverFoto}
              onClick={() => {
                onChange(null)
                setArquivoOriginal(null)
              }}
              disabled={disabled}
              aria-label="Remover foto"
            >
              <X size={16} />
            </button>
          </div>
        )}

        {arquivoParaRecorte && (
          <CropperFoto
            arquivo={arquivoParaRecorte}
            formato={formato}
            onCancelar={() => setArquivoParaRecorte(null)}
            onConfirmar={handleConfirmarRecorte}
          />
        )}
      </>
    )
  }
)
