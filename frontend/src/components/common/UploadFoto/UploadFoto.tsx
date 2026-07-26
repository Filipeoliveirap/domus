'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { Camera, Crop, Trash2, Loader2, Pencil, Eye, X } from 'lucide-react'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useUploadFoto } from '@/hooks/foto/useUploadFoto'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { CropperFoto } from './CropperFoto'
import styles from './UploadFoto.module.css'

const TIPOS_ACEITOS = ['image/jpeg', 'image/png']
const TAMANHO_MAXIMO_BYTES = 5 * 1024 * 1024

interface Props {
  /** Id da foto já salva (o que vira `urlFoto(valor)`), ou `null`/`undefined` sem foto. */
  valor: string | null | undefined
  onChange: (id: string | null) => void
  /** Círculo (pessoa, logo da igreja) exige recorte; banner (capa de evento) não. */
  formato: 'circulo' | 'banner'
  /** Nome usado só para o fallback de iniciais quando ainda não há foto — nunca mostramos
   * uma silhueta genérica (convenção do sistema); sem nome, cai no ícone de câmera. */
  nomeFallback?: string
  disabled?: boolean
}

/**
 * Área de upload de foto: clique ou arraste, prévia local imediata, recorte (obrigatório
 * em `circulo`, opcional em `banner`) e envio para a API — que devolve o `id` a guardar
 * no formulário. O componente não sabe onde o `id` será usado; quem chama decide (campo
 * de pessoa, evento ou logo da igreja).
 */
export function UploadFoto({ valor, onChange, formato, nomeFallback, disabled = false }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const upload = useUploadFoto()

  const [arquivoBruto, setArquivoBruto] = useState<File | null>(null)
  const [recortando, setRecortando] = useState(false)
  const [arrastandoSobre, setArrastandoSobre] = useState(false)
  const [visualizando, setVisualizando] = useState(false)

  // Prévia local antes de qualquer round-trip de rede — a pessoa vê o que escolheu na
  // hora. O object URL é recriado a cada seleção e revogado assim que deixa de ser usado
  // (troca de arquivo ou desmontagem), senão cada escolha vaza memória.
  const previaLocal = useMemo(
    () => (arquivoBruto ? URL.createObjectURL(arquivoBruto) : null),
    [arquivoBruto],
  )
  useEffect(() => {
    return () => {
      if (previaLocal) URL.revokeObjectURL(previaLocal)
    }
  }, [previaLocal])

  function validarArquivo(arquivo: File): boolean {
    if (!TIPOS_ACEITOS.includes(arquivo.type)) {
      notificar.erro('Formato não aceito', 'Envie uma imagem JPEG ou PNG.')
      return false
    }
    // Checagem no cliente só para poupar upload em vão (ex.: 4G) — quem decide de
    // verdade é o servidor.
    if (arquivo.size > TAMANHO_MAXIMO_BYTES) {
      notificar.erro('Imagem grande demais', 'O tamanho máximo é 5 MB.')
      return false
    }
    return true
  }

  function selecionarArquivo(arquivo: File) {
    if (!validarArquivo(arquivo)) return
    setArquivoBruto(arquivo)
    // Círculo (pessoa/logo) sempre passa pelo recorte; banner deixa a pessoa escolher.
    if (formato === 'circulo') setRecortando(true)
  }

  function aoEscolherArquivo(e: React.ChangeEvent<HTMLInputElement>) {
    const arquivo = e.target.files?.[0]
    e.target.value = ''
    if (arquivo) selecionarArquivo(arquivo)
  }

  function aoSoltarArquivo(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setArrastandoSobre(false)
    const arquivo = e.dataTransfer.files?.[0]
    if (arquivo) selecionarArquivo(arquivo)
  }

  function enviar(arquivo: File) {
    upload.mutate(arquivo, {
      onSuccess: (res) => {
        onChange(res.id)
        setArquivoBruto(null)
        setRecortando(false)
      },
    })
  }

  function cancelarSelecao() {
    setArquivoBruto(null)
    setRecortando(false)
  }

  function remover() {
    onChange(null)
    setArquivoBruto(null)
  }

  async function editarFotoExistente() {
    if (!urlAtual) return
    try {
      const res = await fetch(urlAtual)
      const blob = await res.blob()
      const file = new File([blob], 'foto-atual.jpg', { type: blob.type || 'image/jpeg' })
      selecionarArquivo(file)
    } catch {
      notificar.erro('Erro ao carregar a foto para edição.')
    }
  }

  const enviando = upload.isPending
  const urlAtual = urlFoto(valor, 'THUMB')
  const temAlgumaImagem = Boolean(previaLocal || urlAtual)

  return (
    <div className={styles.raiz}>
      <div
        className={`${styles.area} ${formato === 'circulo' ? styles.areaCirculo : styles.areaBanner} ${
          arrastandoSobre ? styles.arrastando : ''
        }`}
        onClick={() => {
          if (disabled || enviando) return
          if (urlAtual && !arquivoBruto) setVisualizando(true)
          else inputRef.current?.click()
        }}
        onDragOver={(e) => {
          e.preventDefault()
          if (!disabled) setArrastandoSobre(true)
        }}
        onDragLeave={() => setArrastandoSobre(false)}
        onDrop={(e) => !disabled && aoSoltarArquivo(e)}
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-label="Selecionar foto"
        aria-disabled={disabled}
      >
        {enviando ? (
          <div className={styles.estadoEnvio}>
            <Loader2 size={22} className={styles.spinner} aria-hidden="true" />
            <span>Enviando…</span>
          </div>
        ) : previaLocal ? (
          // eslint-disable-next-line @next/next/no-img-element -- prévia local (blob), não vem da API de fotos
          <img src={previaLocal} alt="Prévia da foto selecionada" className={styles.imagem} />
        ) : urlAtual ? (
          // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos, coberta pelo CSP img-src 'self'
          <img src={urlAtual} alt="Foto atual" className={styles.imagem} />
        ) : nomeFallback && formato === 'circulo' ? (
          <span className={styles.iniciais}>{iniciais(nomeFallback)}</span>
        ) : (
          <div className={styles.placeholder}>
            <Camera size={22} aria-hidden="true" />
            <span>Clique ou arraste uma foto</span>
            <span className={styles.ajuda}>JPEG ou PNG, até 5 MB</span>
          </div>
        )}

        {!enviando && temAlgumaImagem && (
          <div className={styles.badgeCamera} aria-hidden="true">
            {urlAtual && !arquivoBruto ? <Eye size={14} /> : <Camera size={14} />}
          </div>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={TIPOS_ACEITOS.join(',')}
        className={styles.inputOculto}
        onChange={aoEscolherArquivo}
        disabled={disabled}
      />

      {!enviando && temAlgumaImagem && (
        <div className={styles.acoes}>
          {formato === 'banner' && arquivoBruto && !recortando && (
            <>
              <button type="button" className={styles.botaoSecundario} onClick={() => setRecortando(true)}>
                <Crop size={14} aria-hidden="true" />
                Ajustar recorte
              </button>
              <button type="button" className={styles.botaoPrimario} onClick={() => enviar(arquivoBruto)}>
                Usar esta foto
              </button>
            </>
          )}

          {!arquivoBruto && (valor || urlAtual) && (
            <>
              <button type="button" className={styles.botaoEditar} onClick={editarFotoExistente} disabled={disabled || !urlAtual}>
                <Pencil size={14} aria-hidden="true" />
                Editar foto
              </button>
              <button type="button" className={styles.botaoRemover} onClick={remover} disabled={disabled}>
                <Trash2 size={14} aria-hidden="true" />
                Remover foto
              </button>
            </>
          )}
        </div>
      )}

      {recortando && arquivoBruto && (
        <CropperFoto
          arquivo={arquivoBruto}
          formato={formato}
          onCancelar={cancelarSelecao}
          onConfirmar={enviar}
        />
      )}

      {visualizando && urlAtual && (
        <div className={styles.viewerOverlay} onMouseDown={() => setVisualizando(false)}>
          <div className={styles.viewerModal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.viewerClose} onClick={() => setVisualizando(false)}>
              <X size={20} />
            </button>
            <img
              src={urlFoto(valor, 'DISPLAY')!}
              alt="Foto"
              className={styles.viewerImg}
            />
            <div className={styles.viewerAcoes}>
              <button className={styles.botaoEditar} onClick={() => { setVisualizando(false); editarFotoExistente() }}>
                <Pencil size={14} /> Editar
              </button>
              <button className={styles.botaoRemover} onClick={() => { setVisualizando(false); remover() }}>
                <Trash2 size={14} /> Remover
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
