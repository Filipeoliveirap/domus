'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { X, Image as ImageIcon, Pencil, Check } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useAtualizarPostagem } from '@/hooks/postagem/useAtualizarPostagem'
import { CropperFoto } from '@/components/common/UploadFoto/CropperFoto'
import { notificar } from '@/components/common/Notificacao/notificar'
import { urlFoto } from '@/lib/urlFoto'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { Postagem, TipoPostagem } from '@/types/postagem.type'
import styles from './ModalEditarPostagem.module.css'

const TAGS_CATEGORIA: { label: string; valor: TipoPostagem }[] = [
  { label: 'Devocional', valor: 'DEVOCIONAL' },
  { label: 'Resumo do Culto', valor: 'RESUMO_CULTO' },
  { label: 'Pedido de Oração', valor: 'PEDIDO_ORACAO' },
  { label: 'Testemunho', valor: 'TESTEMUNHO' },
  { label: 'Geral', valor: 'GERAL' },
]

interface Props {
  postagem: Postagem
  aoFechar: () => void
}

export function ModalEditarPostagem({ postagem, aoFechar }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const atualizarPost = useAtualizarPostagem()

  const [conteudo, setConteudo] = useState(postagem.conteudo ?? '')
  const [tipo, setTipo] = useState<TipoPostagem>(postagem.tipo)
  const [fotoId, setFotoId] = useState<string | null>(postagem.fotoId ?? null)
  const [carregandoFoto, setCarregandoFoto] = useState(false)
  const [arquivoParaRecorte, setArquivoParaRecorte] = useState<File | null>(null)
  const [arquivoOriginal, setArquivoOriginal] = useState<File | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !atualizarPost.isPending) fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, atualizarPost.isPending])

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
      setFotoId(res.data.id)
    } catch {
      notificar.erro('Não foi possível enviar a foto', 'Tente enviar a imagem novamente.')
    } finally {
      setCarregandoFoto(false)
    }
  }

  const podeSalvar = Boolean(conteudo.trim() || fotoId)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!podeSalvar) return

    atualizarPost.mutate(
      {
        postagemId: postagem.id,
        payload: {
          tipo,
          conteudo: conteudo.trim(),
          fotoId: fotoId ?? null,
        },
      },
      {
        onSuccess: () => {
          notificar.sucesso('Postagem atualizada', 'Sua postagem foi alterada com sucesso.')
          fechar()
        },
        onError: () => {
          notificar.erro('Não foi possível salvar', 'Tente novamente.')
        },
      },
    )
  }

  const urlPreview = urlFoto(fotoId, 'DISPLAY')

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !atualizarPost.isPending && fechar()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo}>Editar Postagem</h2>
          <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.modalCorpo}>
            <textarea
              className={styles.textarea}
              rows={4}
              placeholder="Digite o texto da postagem..."
              value={conteudo}
              onChange={(e) => setConteudo(e.target.value)}
            />

            {fotoId && urlPreview && (
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
                >
                  <Pencil size={13} />
                  Editar / Trocar
                </button>
                <Image
                  src={urlPreview}
                  alt="Prévia da foto"
                  width={600}
                  height={280}
                  unoptimized
                  className={styles.previewImagem}
                />
                <button
                  type="button"
                  className={styles.btnRemoverFoto}
                  onClick={() => {
                    setFotoId(null)
                    setArquivoOriginal(null)
                  }}
                  aria-label="Remover foto"
                >
                  <X size={16} />
                </button>
              </div>
            )}

            <div className={styles.seletorTags}>
              {TAGS_CATEGORIA.map((tag) => (
                <button
                  key={tag.valor}
                  type="button"
                  className={`${styles.chipTag} ${tipo === tag.valor ? styles.chipTagActive : ''}`}
                  onClick={() => setTipo(tag.valor)}
                >
                  {tag.label}
                </button>
              ))}
            </div>
          </div>

          <div className={styles.modalRodape}>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="file"
                ref={fileInputRef}
                onChange={handleSelecionarArquivo}
                accept="image/*"
                style={{ display: 'none' }}
              />
              <button
                type="button"
                className={styles.btnAcaoIcone}
                title="Adicionar ou alterar foto"
                onClick={() => fileInputRef.current?.click()}
                disabled={carregandoFoto}
              >
                <ImageIcon size={18} />
              </button>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="button" className={styles.btnCancelar} onClick={fechar} disabled={atualizarPost.isPending}>
                Cancelar
              </button>
              <button
                type="submit"
                className={styles.btnSalvar}
                disabled={atualizarPost.isPending || carregandoFoto || !podeSalvar}
              >
                <Check size={14} />
                {atualizarPost.isPending ? 'Salvando...' : 'Salvar Alterações'}
              </button>
            </div>
          </div>
        </form>
      </div>

      {arquivoParaRecorte && (
        <CropperFoto
          arquivo={arquivoParaRecorte}
          formato="banner"
          onCancelar={() => setArquivoParaRecorte(null)}
          onConfirmar={handleConfirmarRecorte}
        />
      )}
    </div>
  )
}
