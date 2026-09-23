'use client'

import { useState } from 'react'
import Image from 'next/image'
import { Send, Image as ImageIcon, BookOpen, Heart } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useCriarPostagem } from '@/hooks/postagem/useCriarPostagem'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
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
  const nome = useAuthStore((s) => s.nome) ?? 'Membro'
  const fotoId = useAuthStore((s) => s.fotoId)
  const url = urlFoto(fotoId, 'THUMB')

  const criarPostagem = useCriarPostagem()
  const [conteudo, setConteudo] = useState('')
  const [tipo, setTipo] = useState<TipoPostagem>('DEVOCIONAL')
  const [versiculoRef, setVersiculoRef] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!conteudo.trim()) return

    criarPostagem.mutate(
      {
        tipo,
        oficial: false,
        conteudo: conteudo.trim(),
        versiculoRef: versiculoRef.trim() || undefined,
      },
      {
        onSuccess: () => {
          setConteudo('')
          setVersiculoRef('')
        },
      },
    )
  }

  return (
    <form className={styles.caixa} onSubmit={handleSubmit}>
      <div className={styles.autorLinha}>
        <div className={styles.avatar}>
          {url ? (
            <Image src={url} alt="" width={40} height={40} unoptimized style={{ borderRadius: '50%' }} />
          ) : (
            iniciais(nome)
          )}
        </div>
        <div className={styles.corpoInput}>
          <span className={styles.nomeAutor}>{nome}</span>
          <textarea
            className={styles.textarea}
            rows={2}
            placeholder="O que está em seu coração hoje? Compartilhe um devocional, testemunho ou resumo..."
            value={conteudo}
            onChange={(e) => setConteudo(e.target.value)}
          />
        </div>
      </div>

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
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            type="button"
            className={styles.btnAcaoIcone}
            title="Inserir Versículo"
            onClick={() => {
              const ref = prompt('Digite a referência do versículo (ex: Provérbios 3:5-6):')
              if (ref) setVersiculoRef(ref)
            }}
          >
            <BookOpen size={18} />
          </button>
          <button
            type="button"
            className={styles.btnAcaoIcone}
            title="Pedido de Oração"
            onClick={() => setTipo('PEDIDO_ORACAO')}
          >
            <Heart size={18} />
          </button>
        </div>

        <button
          type="submit"
          className={styles.btnPublicar}
          disabled={criarPostagem.isPending || !conteudo.trim()}
        >
          <Send size={14} />
          {criarPostagem.isPending ? 'Publicando...' : 'Publicar'}
        </button>
      </div>
    </form>
  )
}
