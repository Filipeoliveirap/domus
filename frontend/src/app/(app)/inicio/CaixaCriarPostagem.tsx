'use client'

import { useRef, useState } from 'react'
import Image from 'next/image'
import { Send, Image as ImageIcon, X } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useCriarPostagem } from '@/hooks/postagem/useCriarPostagem'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
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

  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleUploadFoto = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    try {
      setCarregandoFoto(true)
      const formData = new FormData()
      formData.append('file', file)

      const res = await api.post<{ id: string }>(Endpoints.fotos.UPLOAD, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setFotoId(res.data.id)
    } catch {
      alert('Erro ao carregar a foto. Tente novamente.')
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

  const urlPreviewFoto = urlFoto(fotoId, 'THUMB')

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
        <div style={{ position: 'relative', width: 80, height: 80, marginLeft: 52 }}>
          <Image src={urlPreviewFoto} alt="Prévia" width={80} height={80} unoptimized style={{ borderRadius: 8, objectFit: 'cover' }} />
          <button
            type="button"
            onClick={() => setFotoId(null)}
            style={{ position: 'absolute', top: -4, right: -4, background: '#ba1a1a', color: '#fff', border: 'none', borderRadius: '50%', padding: 2, cursor: 'pointer' }}
          >
            <X size={12} />
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
            onChange={handleUploadFoto}
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
    </form>
  )
}
