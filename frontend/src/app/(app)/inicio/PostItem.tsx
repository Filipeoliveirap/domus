'use client'

import { useState } from 'react'
import Image from 'next/image'
import { Heart, MessageSquare, Send } from 'lucide-react'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { useCurtirPostagem } from '@/hooks/postagem/useCurtirPostagem'
import { useComentarPostagem } from '@/hooks/postagem/useComentarPostagem'
import { Colapsavel } from '@/components/common/Transicao/Colapsavel'
import type { Postagem } from '@/types/postagem.type'
import styles from './PostItem.module.css'

export function PostItem({ postagem }: { postagem: Postagem }) {
  const curtir = useCurtirPostagem()
  const comentar = useComentarPostagem()

  const [comentariosAbertos, setComentariosAbertos] = useState(false)
  const [novoComentario, setNovoComentario] = useState('')

  const url = urlFoto(postagem.autor.fotoId, 'THUMB')

  const handleCurtir = () => {
    curtir.mutate({ postagemId: postagem.id, tipo: 'AMEM' })
  }

  const handleComentar = (e: React.FormEvent) => {
    e.preventDefault()
    if (!novoComentario.trim()) return

    comentar.mutate(
      { postagemId: postagem.id, conteudo: novoComentario.trim() },
      {
        onSuccess: () => setNovoComentario(''),
      },
    )
  }

  return (
    <article className={styles.postCard}>
      <div className={styles.cabecalho}>
        <div className={styles.autorBloco}>
          <div className={styles.avatar}>
            {url ? (
              <Image src={url} alt="" width={40} height={40} unoptimized style={{ borderRadius: '50%' }} />
            ) : (
              iniciais(postagem.autor.nome)
            )}
          </div>
          <div className={styles.autorInfo}>
            <span className={styles.nomeAutor}>{postagem.autor.nome}</span>
            <span className={styles.metaPost}>
              {new Date(postagem.criadoEm).toLocaleDateString('pt-BR')}
            </span>
          </div>
        </div>
        <span className={styles.tagPost}>{postagem.tipo.replace('_', ' ')}</span>
      </div>

      <p className={styles.conteudo}>{postagem.conteudo}</p>

      {postagem.versiculoRef && (
        <div className={styles.blocoVersiculo}>
          &ldquo;{postagem.conteudo.slice(0, 100)}...&rdquo;
          <span className={styles.refVersiculo}>— {postagem.versiculoRef}</span>
        </div>
      )}

      <div className={styles.barraReacoes}>
        <div className={styles.grupoCurtidas}>
          <button type="button" className={styles.btnAmem} onClick={handleCurtir}>
            <Heart size={15} fill={postagem.minhaReacao ? 'currentColor' : 'none'} />
            Amém ({postagem.totalCurtidas})
          </button>
        </div>

        <button
          type="button"
          className={styles.btnComentarToggle}
          onClick={() => setComentariosAbertos(!comentariosAbertos)}
        >
          <MessageSquare size={15} />
          {postagem.totalComentarios} comentários
        </button>
      </div>

      <Colapsavel aberto={comentariosAbertos}>
        <div className={styles.secaoComentarios}>
          {(postagem.comentariosRecentes ?? []).map((c) => (
            <div key={c.id} className={styles.itemComentario}>
              <span className={styles.autorComentario}>{c.autor.nome}:</span>
              <span>{c.conteudo}</span>
            </div>
          ))}

          <form className={styles.formComentario} onSubmit={handleComentar}>
            <input
              type="text"
              className={styles.inputComentario}
              placeholder="Escreva um comentário..."
              value={novoComentario}
              onChange={(e) => setNovoComentario(e.target.value)}
            />
            <button
              type="submit"
              className={styles.btnAmem}
              disabled={comentar.isPending || !novoComentario.trim()}
            >
              <Send size={12} />
            </button>
          </form>
        </div>
      </Colapsavel>
    </article>
  )
}
