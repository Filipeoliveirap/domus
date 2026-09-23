'use client'

import { useRef, useState } from 'react'
import Image from 'next/image'
import { Send, Image as ImageIcon, X, Pencil } from 'lucide-react'
import { CropperFoto } from '@/components/common/UploadFoto/CropperFoto'
import { useAuthStore } from '@/store/authStore'
import { useCriarPostagem } from '@/hooks/postagem/useCriarPostagem'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { TipoPostagem } from '@/types/postagem.type'
import styles from './CaixaCriarPostagem.module.css'

const TAGS_CATEGORIA: { label: string; valor: TipoPostagem }[] = [
  { label: 'Devocional', valor: 'DEVOCIONAL' },
  { label: 'Resumo do Culto', valor: 'RESUMO_CULTO' },
  { label: 'Pedido de Oração', valor: 'PEDIDO_ORACAO' },
  { label: 'Testemunho', valor: 'TESTEMUNHO' },
  { label: 'Geral', valor: 'GERAL' },
]

export function CaixaCriarPostagem() {
  const nomeCompleto = useAuthStore((s) => s.nome) ?? 'Membro'
  const nomeCurto = doisPrimeirosNomes(nomeCompleto)
  const fotoIdPerfil = useAuthStore((s) => s.fotoId)
  const urlPerfil = urlFoto(fotoIdPerfil, 'THUMB')

  const criarPostagem = useCriarPostagem()
  const [conteudo, setConteudo] = useState('')
  const [tipo, setTipo] = useState<TipoPostagem>('DEVOCIONAL')
  const [fotoId, setFotoId] = useState<string | null>(null)
  const [carregandoFoto, setCarregandoFoto] = useState(false)
  const [arquivoParaRecorte, setArquivoParaRecorte] = useState<File | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleSelecionarArquivo = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setArquivoParaRecorte(file)
    // Limpa valor do input para permitir re-selecionar o mesmo arquivo se necessário
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

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!conteudo.trim()) return

    criarPostagem.mutate(
      {
        tipo,
        oficial: false,
        conteudo: conteudo.trim(),
        fotoId: fotoId || undefined,
      },
      {
        onSuccess: () => {
          setConteudo('')
          setFotoId(null)
        },
      },
    )
  }

  const urlPreviewFoto = urlFoto(fotoId, 'DISPLAY')

  return (
    <form className={styles.caixa} onSubmit={handleSubmit}>
      <div className={styles.autorLinha}>
        <div className={styles.avatar}>
          {urlPerfil ? (
            <Image src={urlPerfil} alt="" width={40} height={40} unoptimized style={{ borderRadius: '50%' }} />
          ) : (
            iniciais(nomeCompleto)
          )}
        </div>
        <div className={styles.corpoInput}>
          <span className={styles.nomeAutor}>{nomeCurto}</span>
          <textarea
            className={styles.textarea}
            rows={2}
            placeholder="O que está em seu coração hoje? Compartilhe um devocional, testemunho ou resumo..."
            value={conteudo}
            onChange={(e) => setConteudo(e.target.value)}
          />
        </div>
      </div>

      {fotoId && urlPreviewFoto && (
        <div className={styles.previewContainer}>
          <button
            type="button"
            className={styles.btnEditarFoto}
            onClick={() => fileInputRef.current?.click()}
          >
            <Pencil size={13} />
            Editar
          </button>
          <Image
            src={urlPreviewFoto}
            alt="Prévia da imagem"
            width={600}
            height={260}
            unoptimized
            className={styles.previewImagem}
          />
          <button
            type="button"
            className={styles.btnRemoverFoto}
            onClick={() => setFotoId(null)}
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

      <div className={styles.barraAcoes}>
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
            title="Adicionar Foto"
            onClick={() => fileInputRef.current?.click()}
            disabled={carregandoFoto}
          >
            <ImageIcon size={18} />
          </button>
          {carregandoFoto && <span style={{ fontSize: 11, color: '#737686' }}>Enviando foto...</span>}
        </div>

        <button
          type="submit"
          className={styles.btnPublicar}
          disabled={criarPostagem.isPending || carregandoFoto || !conteudo.trim()}
        >
          <Send size={14} />
          {criarPostagem.isPending ? 'Publicando...' : 'Publicar'}
        </button>
      </div>

      {arquivoParaRecorte && (
        <CropperFoto
          arquivo={arquivoParaRecorte}
          formato="banner"
          onCancelar={() => setArquivoParaRecorte(null)}
          onConfirmar={handleConfirmarRecorte}
        />
      )}
    </form>
  )
}
