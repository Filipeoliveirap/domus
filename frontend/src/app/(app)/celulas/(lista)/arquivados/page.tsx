'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, Grid3X3 } from 'lucide-react'
import { useCelulasArquivadas } from '@/hooks/celula/useCelulasArquivadas'
import { useRestaurarCelula } from '@/hooks/celula/useRestaurarCelula'
import { useExcluirCelulaDefinitivamente } from '@/hooks/celula/useExcluirCelulaDefinitivamente'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import type { CelulaResponse } from '@/types/celula.type'
import styles from './arquivados.module.css'

export default function CelulasArquivadasPage() {
  const { data: celulas, isLoading, isError, refetch } = useCelulasArquivadas()
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarCelulas(role)
  const { restaurar, isLoading: restaurando } = useRestaurarCelula()
  const [excluindo, setExcluindo] = useState<CelulaResponse | null>(null)

  if (!podeGerenciar) {
    return <EstadoErro titulo="Sem acesso" mensagem="Só administradores veem células arquivadas." />
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
    return <EstadoVazio icone={Archive} titulo="Nenhuma célula arquivada" mensagem="Células arquivadas aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {celulas.map((c) => (
          <div key={c.id} className={styles.linha}>
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
            <div className={styles.acoes}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(c.id, c.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button
                className={styles.botaoExcluir}
                disabled={c.temVinculo}
                title={c.temVinculo ? 'Tem membro vinculado — remova todos antes de excluir.' : undefined}
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
  return (
    <ModalConfirmacaoCritica
      titulo="Excluir célula definitivamente?"
      mensagem={
        <>
          Isso vai apagar <strong>{celula.nome}</strong> de vez. Não tem como desfazer.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: 'A célula deixa de existir em qualquer lugar do sistema' },
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
