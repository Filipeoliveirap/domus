'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Pencil, Archive, Grid3X3, Crown, Trash2 } from 'lucide-react'
import { useCelulas } from '@/hooks/celula/useCelulas'
import { useExcluirCelulaDefinitivamente } from '@/hooks/celula/useExcluirCelulaDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { urlFoto } from '@/lib/urlFoto'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { celulaService } from '@/services/celula.service'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { CelulaResponse } from '@/types/celula.type'
import styles from './page.module.css'
import { ModalCelulaForm } from './ModalCelulaForm'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'

// Rótulo de líder do card — nome do 1º líder + contagem, ou "Sem líder". Mesmo padrão de Ministério.
function rotuloLideres(lideres: string[]): string {
  if (lideres.length === 0) return 'Sem líder'
  if (lideres.length === 1) return lideres[0]
  return `${lideres[0]} +${lideres.length - 1}`
}

export default function CelulasPage() {
  const router = useRouter()
  const { data: celulas, isLoading, isError, refetch } = useCelulas()
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const podeGerenciar = podeGerenciarCelulas(role, capacidadesExtras)
  // `null` = fechado; `'novo'` = criar; objeto = editar. Mesma convenção do ModalMinisterioForm.
  const [formAberto, setFormAberto] = useState<'novo' | CelulaResponse | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)
  const [excluindoDefinitivo, setExcluindoDefinitivo] = useState<CelulaResponse | null>(null)
  const [arquivando, setArquivando] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const { celula: rotuloCelula } = useRotulos()

  async function handleToggleArquivar(id: string) {
    if (arquivando) return
    const celula = celulas?.find((c) => c.id === id)
    if (!celula) return
    setArquivando(id)
    try {
      await celulaService.excluir(id)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${celula.nome} foi removida.`)
    } catch {
      notificar.erro(`Erro ao remover ${rotuloCelula.singular.toLowerCase()}.`)
    } finally {
      setArquivando(null)
    }
  }

  if (!hidratado) return <div className={styles.pagina} />

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>{rotuloCelula.plural}</h1>
            {celulas && celulas.length > 0 && <span className={styles.contador}>{celulas.length}</span>}
          </div>
          <p className={styles.subtitulo}>Pequenos grupos de estudo bíblico.</p>
        </div>
        {podeGerenciar && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setFormAberto('novo')}>
            Nova {rotuloCelula.singular.toLowerCase()}
          </button>
        )}
      </header>

      {isLoading ? (
        <div className={styles.grid}>
          {[1, 2, 3].map((i) => (
            <div key={i} className={styles.card}>
              <Skeleton width="64px" height="64px" radius="var(--radius-lg)" />
              <Skeleton width="70%" height="18px" />
              <Skeleton width="50%" height="14px" />
            </div>
          ))}
        </div>
      ) : isError ? (
        <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão."
          aoTentarNovamente={() => refetch()} />
      ) : celulas && celulas.length === 0 ? (
        <EstadoVazio icone={Grid3X3} titulo={`Nenhuma ${rotuloCelula.singular.toLowerCase()}`}
          mensagem={`Comece cadastrando a primeira ${rotuloCelula.singular.toLowerCase()} da sua igreja.`}
          acaoPrimaria={podeGerenciar ? { label: `Nova ${rotuloCelula.singular.toLowerCase()}`, onClick: () => setFormAberto('novo') } : undefined} />
      ) : (
        <div className={styles.grid}>
          {celulas?.map((c) => {
            const podeEditarEsta = podeGerenciar || c.souLiderDestaCelula
            const acoes: ItemAcao[] = [
              ...(podeEditarEsta ? [{ label: 'Editar', icone: Pencil, onClick: () => setFormAberto(c) }] : []),
              ...(podeGerenciar
                ? [c.temVinculo
                    ? { label: 'Arquivar', icone: Archive, onClick: () => handleToggleArquivar(c.id), perigo: true, separadorAntes: true }
                    : { label: 'Excluir', icone: Trash2, onClick: () => setExcluindoDefinitivo(c), perigo: true, separadorAntes: true }]
                : []),
            ]
            return (
              <div key={c.id} className={styles.card}
                role="button" tabIndex={0}
                onClick={() => router.push(`/celulas/${c.id}`)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') router.push(`/celulas/${c.id}`) }}
              >
                {acoes.length > 0 && (
                  <div className={styles.cardActions} onClick={(e) => e.stopPropagation()}>
                    <MenuAcoes itens={acoes} />
                  </div>
                )}
                {c.fotoId ? (
                  <img src={urlFoto(c.fotoId, 'THUMB')!} alt="" className={styles.cardFoto}
                    onClick={(e) => { e.stopPropagation(); setFotoVisualizando(c.fotoId) }} />
                ) : (
                  <div className={styles.cardIcon}>
                    <Grid3X3 size={24} />
                  </div>
                )}
                <div className={styles.cardTopo}>
                  <span className={styles.cardTitulo}>{c.nome}</span>
                </div>
                {(c.diaSemana || c.horario) && (
                  <p className={styles.cardHorario}>
                    {[rotuloDiaSemana(c.diaSemana), formatarHorario(c.horario)].filter(Boolean).join(', ')}
                  </p>
                )}
                <div className={styles.cardLider}>
                  <Crown size={14} />
                  <span>{rotuloLideres(c.lideres)}</span>
                </div>
                <div className={styles.cardMembros}>
                  {c.totalMembros} {c.totalMembros === 1 ? 'membro' : 'membros'}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {formAberto && (
        <ModalCelulaForm celula={formAberto === 'novo' ? null : formAberto} onClose={() => setFormAberto(null)} />
      )}

      {fotoVisualizando && (
        <VisualizadorFoto fotoId={fotoVisualizando} descricao="Foto de perfil" onClose={() => setFotoVisualizando(null)} />
      )}

      {excluindoDefinitivo && (
        <ModalExcluirDefinitivo celula={excluindoDefinitivo} onClose={() => setExcluindoDefinitivo(null)} />
      )}
    </div>
  )
}

function ModalExcluirDefinitivo({ celula, onClose }: { celula: CelulaResponse; onClose: () => void }) {
  // Esse botão só aparece quando a célula não tem ninguém vinculado (senão o menu
  // mostra "Arquivar") — confirmação simples basta, sem precisar digitar o nome.
  const { confirmar, isLoading } = useExcluirCelulaDefinitivamente(celula, onClose)
  const { celula: rotuloCelula } = useRotulos()
  return (
    <ModalConfirmacao
      titulo={`Excluir ${rotuloCelula.singular.toLowerCase()} definitivamente?`}
      mensagem={<>Isso vai apagar <strong>{celula.nome}</strong> de vez. Não tem como desfazer.</>}
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
