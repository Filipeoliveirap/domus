'use client'

import { useState } from 'react'
import { Pencil, Archive, MapPinned } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarEventos } from '@/lib/permissoes'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { ModalLocalForm } from '@/components/module/eventos/ModalLocalForm'
import { ModalArquivarLocal } from './ModalArquivarLocal'
import { ModalDetalheLocal } from '@/components/module/eventos/ModalDetalheLocal'
import type { LocalEventoResponse } from '@/types/evento.type'
import styles from './locais.module.css'

export default function LocaisEventoPage() {
  const role = useAuthStore((s) => s.role)
  const hidratado = useAuthStore((s) => s.hidratado)
  const podeGerenciar = podeGerenciarEventos(role)

  const { data: locais = [], isLoading } = useLocaisEvento()

  // Formulário nulo = fechado; objeto vazio-ish não existe — `null` de dado (não `undefined`)
  // distingue "fechado" de "aberto para criar" (edição sempre traz o local escolhido).
  const [formAberto, setFormAberto] = useState<'novo' | LocalEventoResponse | null>(null)
  const [localArquivando, setLocalArquivando] = useState<LocalEventoResponse | null>(null)
  const [localDetalhe, setLocalDetalhe] = useState<LocalEventoResponse | null>(null)

  if (!hidratado || isLoading) {
    return <div className={styles.pagina} />
  }

  // A tela inteira de gestão é restrita — não só os botões de escrever. GET é liberado a
  // qualquer autenticado (alimenta o <SeletorLocal> do formulário de evento), mas ver esta
  // tela específica (com editar/arquivar) é só para quem gerencia eventos. O backend já
  // recusa POST/PUT/DELETE de quem não gerencia — isto é só a UI não oferecer o que falharia.
  if (!podeGerenciar) {
    return <AcessoRestrito />
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>Endereços</h1>
          <p className={styles.subtitulo}>Endereços cadastrados para os eventos da igreja</p>
        </div>
        <button type="button" className={styles.botaoPrimario} onClick={() => setFormAberto('novo')}>
          Novo endereço
        </button>
      </header>

      {locais.length === 0 ? (
        <EstadoVazio
          icone={MapPinned}
          titulo="Nenhum endereço cadastrado"
          mensagem="Cadastre os endereços mais usados para agilizar o cadastro de eventos."
          acaoPrimaria={{ label: 'Novo endereço', onClick: () => setFormAberto('novo') }}
        />
      ) : (
        <div className={styles.containerTabela}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th>Nome</th>
                <th>Capacidade</th>
                <th>Endereço</th>
                <th className={styles.colunaAcoes}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {locais.map((local) => {
                const acoes: ItemAcao[] = [
                  { label: 'Editar', icone: Pencil, onClick: () => setFormAberto(local) },
                  { label: 'Arquivar', icone: Archive, onClick: () => setLocalArquivando(local), perigo: true, separadorAntes: true },
                ]
                return (
                  <tr key={local.id} className={styles.linhaClicavel} onClick={() => setLocalDetalhe(local)}>
                    <td className={styles.nome}>{local.nome}</td>
                    <td>{local.capacidade ?? '—'}</td>
                    <td className={styles.endereco}>
                      {local.endereco ?? '—'}
                      {local.enderecoHerdado && <span className={styles.tagHerdado}>endereço da igreja</span>}
                    </td>
                    <td className={styles.colunaAcoes} onClick={(e) => e.stopPropagation()}>
                      <MenuAcoes itens={acoes} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {formAberto && (
        <ModalLocalForm
          local={formAberto === 'novo' ? null : formAberto}
          onClose={() => setFormAberto(null)}
        />
      )}

      {localArquivando && (
        <ModalArquivarLocal local={localArquivando} onClose={() => setLocalArquivando(null)} />
      )}

      {localDetalhe && (
        <ModalDetalheLocal local={localDetalhe} onClose={() => setLocalDetalhe(null)} />
      )}
    </div>
  )
}
