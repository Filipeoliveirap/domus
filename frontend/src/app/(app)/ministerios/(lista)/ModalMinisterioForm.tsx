'use client'

import { useEffect, useRef, useState } from 'react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useCriarMinisterio, useAtualizarMinisterio, useAtualizarFotoMinisterio } from '@/hooks/ministerio/useMinisterioForm'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './ModalMinisterioForm.module.css'

interface Props {
  ministerio: MinisterioResponse | null
  onClose: () => void
}

// As mutations (useCriarMinisterio/useAtualizarMinisterio) já disparam notificar.sucesso/erro
// sozinhas (ver Task 9) — este componente só decide fechar o modal em caso de sucesso.
export function ModalMinisterioForm({ ministerio, onClose }: Props) {
  const { ministerio: rotuloMinisterio } = useRotulos()
  const [nome, setNome] = useState(ministerio?.nome ?? '')
  const [erroNome, setErroNome] = useState<string | null>(null)
  const [fotoId, setFotoId] = useState<string | null>(ministerio?.fotoId ?? null)
  const criar = useCriarMinisterio()
  const atualizar = useAtualizarMinisterio(ministerio?.id ?? '')
  const atualizarFoto = useAtualizarFotoMinisterio(ministerio?.id ?? '')
  const salvando = criar.isPending || atualizar.isPending
  const { saindo, fechar } = useFecharAnimado(onClose, 260)
  const nomeRef = useRef<HTMLInputElement>(null)

  // Foco no campo só no desktop: no bottom-sheet mobile o autoFocus abre o teclado na hora
  // e empurra o sheet pra cima antes da animação de entrada assentar.
  useEffect(() => {
    if (!window.matchMedia('(min-width: 768px)').matches) return
    const t = window.setTimeout(() => nomeRef.current?.focus(), 60)
    return () => window.clearTimeout(t)
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !salvando) fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, salvando])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  async function salvar() {
    if (!nome.trim()) {
      setErroNome('Dê um nome antes de salvar.')
      const el = nomeRef.current
      el?.focus()
      if (el && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        el.classList.add('tremido')
        window.setTimeout(() => el.classList.remove('tremido'), 450)
      }
      return
    }
    try {
      if (ministerio) {
        await atualizar.mutateAsync({ nome, fotoId })
      } else {
        await criar.mutateAsync({ nome, fotoId })
      }
      fechar()
    } catch {
      // erro já notificado pela mutation; modal fica aberto para o usuário tentar de novo.
    }
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !salvando && fechar()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <h2 className={styles.titulo}>
          {ministerio ? `Editar ${rotuloMinisterio.singular.toLowerCase()}` : `Nova ${rotuloMinisterio.singular.toLowerCase()}`}
        </h2>
        <div className={styles.fotoWrap}>
          <UploadFoto
            valor={fotoId}
            onChange={(id) => {
              setFotoId(id)
              // Em criação, o ministério ainda não existe (sem id) — a foto só é
              // enviada junto do "Salvar". Em edição, salva sozinha ao confirmar o recorte.
              if (!ministerio) return
              const fotoAnterior = fotoId
              atualizarFoto.mutate(id, {
                onSuccess: () => notificar.sucesso(id ? 'Foto atualizada.' : 'Foto removida.'),
                onError: (erro: unknown) => {
                  setFotoId(fotoAnterior)
                  const mensagem =
                    (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
                    'Tente novamente em alguns instantes.'
                  notificar.erro('Não foi possível salvar a foto', mensagem)
                },
              })
            }}
            formato="circulo"
            nomeFallback={nome}
          />
        </div>
        <Input
          id="nome-ministerio"
          label="NOME*"
          value={nome}
          onChange={(e) => { setNome(e.target.value); if (erroNome) setErroNome(null) }}
          placeholder="Ex.: Louvor"
          error={erroNome ?? undefined}
          ref={nomeRef}
        />
        <div className={styles.acoes}>
          <Button type="button" variant="secondary" onClick={fechar} disabled={salvando}>
            Cancelar
          </Button>
          <Button type="button" variant="primary" disabled={salvando} isLoading={salvando} loadingText="Salvando…" onClick={salvar}>
            Salvar
          </Button>
        </div>
      </div>
    </div>
  )
}
