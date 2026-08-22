'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Archive, RotateCcw, Trash2, Grid3X3 } from 'lucide-react'
import { useCelulasArquivadas } from '@/hooks/celula/useCelulasArquivadas'
import { useRestaurarCelula } from '@/hooks/celula/useRestaurarCelula'
import { useExcluirCelulaDefinitivamente } from '@/hooks/celula/useExcluirCelulaDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import type { CelulaResponse } from '@/types/celula.type'
import styles from './arquivados.module.css'

export default function CelulasArquivadasPage() {
  const { data: celulas, isLoading, isError, refetch } = useCelulasArquivadas()
  const role = useAuthStore((s) => s.role)
  const router = useRouter()
  const podeGerenciar = podeGerenciarCelulas(role)
  const { restaurar, isLoading: restaurando } = useRestaurarCelula()
  const [excluindo, setExcluindo] = useState<CelulaResponse | null>(null)
  const { celula: rotuloCelula, concordar } = useRotulos()

  if (!podeGerenciar) {
    return <EstadoErro titulo="Sem acesso" mensagem={`Só administradores veem ${rotuloCelula.plural.toLowerCase()} ${concordar(rotuloCelula.genero, 'arquivados')}.`} />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map(i => <Skeleton key={i} width="100%" height="72px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!celulas || celulas.length === 0) {
    return <EstadoVazio icone={Archive}
      titulo={`${concordar(rotuloCelula.genero, 'nenhum')} ${rotuloCelula.singular.toLowerCase()} ${concordar(rotuloCelula.genero, 'arquivado')}`}
      mensagem={`${rotuloCelula.plural} ${concordar(rotuloCelula.genero, 'arquivados')} aparecem aqui.`} />
  }

  return (
    <>
      <div className={styles.lista}>
        {celulas.map((c) => (
          <div key={c.id} className={styles.linha} onClick={() => router.push(`/celulas/${c.id}`)}>
            <div className={styles.info}>
              <div className={styles.icone}><Grid3X3 size={18} /></div>
              <div>
                <p className={styles.nome}>{c.nome}</p>
                {(c.diaSemana || c.horario) && (
                  <p className={styles.detalhe}>
                    {[rotuloDiaSemana(c.diaSemana), formatarHorario(c.horario)].filter(Boolean).join(', ')}
                  </p>
                )}
              </div>
            </div>
            <div className={styles.acoes} onClick={e => e.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(c.id, c.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button
                className={styles.botaoExcluir}
                onClick={() => setExcluindo(c)}
              >
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo celula={excluindo} onClose={() => setExcluindo(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ celula, onClose }: { celula: CelulaResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirCelulaDefinitivamente(celula, onClose)
  const { celula: rotuloCelula } = useRotulos()

  // Sem ninguém vinculado: confirmação simples basta. Com gente vinculada, a pessoa
  // precisa ler antes de confirmar — daí o "digite o nome" (ModalConfirmacaoCritica).
  if (!celula.temVinculo) {
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

  return (
    <ModalConfirmacaoCritica
      titulo={`Excluir ${rotuloCelula.singular.toLowerCase()} definitivamente?`}
      mensagem={
        <>
          <strong>{celula.nome}</strong> tem {celula.totalMembros} {celula.totalMembros === 1 ? 'pessoa vinculada' : 'pessoas vinculadas'}.
          Isso não vai apagar essas pessoas nem o histórico delas em outros lugares do
          sistema — só remove o vínculo delas com esta {rotuloCelula.singular.toLowerCase()} específica. A {rotuloCelula.singular.toLowerCase()} em si
          some de vez. Não tem como desfazer.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: `Todos os ${celula.totalMembros} vínculos com esta ${rotuloCelula.singular.toLowerCase()} são removidos` },
        { tipo: 'mantem', texto: 'As pessoas e visitantes continuam existindo normalmente no sistema' },
      ]}
      palavraConfirmacao={celula.nome}
      textoConfirmar="Excluir definitivamente"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
