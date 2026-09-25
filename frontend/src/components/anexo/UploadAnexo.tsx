'use client'

import { useRef, useState } from 'react'
import { Paperclip, X, Loader2, FileText } from 'lucide-react'
import { clsx } from 'clsx'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useUploadAnexo } from '@/hooks/financeiro/contas-a-pagar/useContasAPagar'
import type { AnexoUploadResponse } from '@/types/contas-a-pagar'
import styles from './UploadAnexo.module.css'

const TIPOS_ACEITOS = [
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/webp',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
]
const TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024 // 10 MB

interface UploadAnexoProps {
  valor?: string | null
  anexo?: AnexoUploadResponse | null
  onChange: (id: string | null) => void
  onAnexoChange?: (anexo: AnexoUploadResponse | null) => void
  disabled?: boolean
}

export function UploadAnexo({
  valor,
  anexo,
  onChange,
  onAnexoChange,
  disabled,
}: UploadAnexoProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const upload = useUploadAnexo()
  const [arrastando, setArrastando] = useState(false)

  function validar(arquivo: File): boolean {
    if (!TIPOS_ACEITOS.includes(arquivo.type)) {
      notificar.erro('Tipo de arquivo não aceito', 'Envie um PDF, imagem ou documento Word/Excel.')
      return false
    }
    if (arquivo.size > TAMANHO_MAXIMO_BYTES) {
      notificar.erro('Arquivo grande demais', 'O tamanho máximo é 10 MB.')
      return false
    }
    return true
  }

  function aoEscolher(e: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = e.target.files?.[0]
    e.target.value = ''
    if (!arquivo) return
    if (!validar(arquivo)) return
    enviar(arquivo)
  }

  function aoSoltar(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setArrastando(false)
    const arquivo = e.dataTransfer.files?.[0]
    if (arquivo && validar(arquivo)) enviar(arquivo)
  }

  function enviar(arquivo: File) {
    upload.mutate(arquivo, {
      onSuccess: (res) => {
        onChange(res.id)
        onAnexoChange?.(res)
        notificar.sucesso('Anexo enviado')
      },
      onError: () => {
        notificar.erro('Erro ao enviar anexo', 'Tente novamente.')
      },
    })
  }

  function remover() {
    onChange(null)
    onAnexoChange?.(null)
  }

  if (valor || anexo) {
    return (
      <div className={styles.quadroAnexo}>
        <FileText size={20} className={styles.iconeAnexo} aria-hidden="true" />
        <div className={styles.infoAnexo}>
          <span className={styles.nomeAnexo}>{anexo?.nomeOriginal ?? 'Anexo'}</span>
          {anexo?.bytes && (
            <span className={styles.tamanhoAnexo}>
              {(anexo.bytes / 1024).toFixed(1)} KB
            </span>
          )}
        </div>
        {!disabled && (
          <button
            type="button"
            className={styles.btnRemover}
            onClick={remover}
            aria-label="Remover anexo"
          >
            <X size={16} />
          </button>
        )}
      </div>
    )
  }

  return (
    <div
      className={clsx(styles.zonaUpload, arrastando && styles.arrastando)}
      onDragOver={(e) => { e.preventDefault(); setArrastando(true) }}
      onDragLeave={() => setArrastando(false)}
      onDrop={aoSoltar}
      onClick={() => !disabled && !upload.isPending && inputRef.current?.click()}
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-label="Enviar anexo"
      onKeyDown={(e) => { if (e.key === 'Enter') inputRef.current?.click() }}
    >
      <input
        ref={inputRef}
        type="file"
        accept={TIPOS_ACEITOS.join(',')}
        onChange={aoEscolher}
        className={styles.inputFile}
        disabled={disabled || upload.isPending}
        aria-hidden="true"
        tabIndex={-1}
      />

      {upload.isPending ? (
        <Loader2 size={20} className={styles.spinner} aria-label="Enviando..." />
      ) : (
        <Paperclip size={20} className={styles.iconeUpload} aria-hidden="true" />
      )}
      <span className={styles.textoUpload}>
        {upload.isPending ? 'Enviando...' : 'Anexar arquivo'}
      </span>
      <span className={styles.textoHint}>PDF, imagem ou documento (máx. 10 MB)</span>
    </div>
  )
}
