'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, User } from 'lucide-react'
import { usePessoasArquivadas } from '@/hooks/pessoa/usePessoasArquivadas'
import { useRestaurarPessoa } from '@/hooks/pessoa/useRestaurarPessoa'
import { useExcluirPessoaDefinitivamente } from '@/hooks/pessoa/useExcluirPessoaDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarPessoas } from '@/lib/permissoes'
import type { PessoaArquivadaResponse } from '@/types/pessoa.type'
import styles from './arquivados.module.css'

export default function PessoasArquivadasPage() {
  const { data: pessoas, isLoading, isError, refetch } = usePessoasArquivadas()
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const podeGerenciar = podeGerenciarPessoas(role, capacidadesExtras)
  const { restaurar, isLoading: restaurando } = useRestaurarPessoa()
  const [excluindo, setExcluindo] = useState<PessoaArquivadaResponse | null>(null)
  const [detalheId, setDetalheId] = useState<string | null>(null)

  if (!podeGerenciar) {
    return <EstadoErro titulo="Sem acesso" mensagem="Só administradores e secretários veem pessoas arquivadas." />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map((i) => <Skeleton key={i} width="100%" height="72px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!pessoas || pessoas.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhuma pessoa arquivada" mensagem="Pessoas arquivadas aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {pessoas.map((p) => (
          <div key={p.id} className={styles.linha} onClick={() => setDetalheId(p.id)}>
            <div className={styles.info}>
              <div className={styles.icone}><User size={18} /></div>
              <div>
                <p className={styles.nome}>{p.nome}</p>
                {p.email && <p className={styles.detalhe}>{p.email}</p>}
              </div>
            </div>
            <div className={styles.acoes} onClick={(e) => e.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(p.id, p.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button className={styles.botaoExcluir} onClick={() => setExcluindo(p)}>
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo pessoa={excluindo} onClose={() => setExcluindo(null)} />
      )}

      {detalheId && (
        <DrawerDetalhePessoa pessoaId={detalheId} onClose={() => setDetalheId(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ pessoa, onClose }: { pessoa: PessoaArquivadaResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirPessoaDefinitivamente(pessoa, onClose)

  // Sem nenhum vínculo: confirmação simples. Com vínculo, exige digitar o nome e mostra
  // exatamente o que sai (login, célula/rede) e o que fica anonimizado (relatórios).
  if (!pessoa.temVinculo) {
    return (
      <ModalConfirmacao
        titulo="Excluir pessoa definitivamente?"
        mensagem={<>Isso vai apagar <strong>{pessoa.nome}</strong> de vez. Não tem como desfazer.</>}
        textoConfirmar="Excluir"
        perigo
        isLoading={isLoading}
        onConfirmar={confirmar}
        onClose={onClose}
      />
    )
  }

  const consequencias: { tipo: 'perde' | 'mantem'; texto: string }[] = []
  if (pessoa.temUsuario) {
    consequencias.push({ tipo: 'perde', texto: 'O login (usuário) desta pessoa é apagado de vez' })
  }
  if (pessoa.temCelula) {
    consequencias.push({ tipo: 'perde', texto: 'Sai da célula que participa' })
  }
  if (pessoa.temMinisterio) {
    consequencias.push({ tipo: 'perde', texto: 'Sai de todos os ministérios/redes que participa' })
  }
  if (pessoa.temContribuicao) {
    consequencias.push({
      tipo: 'mantem',
      texto: 'Contribuições financeiras continuam contando nos relatórios, aparecendo como "Pessoa removida do sistema"',
    })
  }
  if (pessoa.temInscricao) {
    consequencias.push({
      tipo: 'mantem',
      texto: 'Inscrições em eventos continuam contando nos relatórios, aparecendo como "Pessoa removida do sistema"',
    })
  }

  return (
    <ModalConfirmacaoCritica
      titulo="Excluir pessoa definitivamente?"
      mensagem={
        <>
          <strong>{pessoa.nome}</strong> tem vínculos no sistema. Isso não apaga o histórico
          financeiro nem de eventos — eles continuam contando, só sem o nome. Não tem como desfazer.
        </>
      }
      consequencias={consequencias}
      palavraConfirmacao={pessoa.nome}
      textoConfirmar="Excluir definitivamente"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
