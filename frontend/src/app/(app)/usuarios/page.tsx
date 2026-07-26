"use client";

import { useState, Suspense} from "react";
import Link from "next/link";
import axios from "axios";
import { ChevronRight, Shield, Ban, Archive, UserCheck, Send, X } from "lucide-react";
import { notificar } from "@/components/common/Notificacao/notificar";
import { useQueryClient } from "@tanstack/react-query";
import { invalidarCache } from "@/lib/cacheInvalidacao";
import { pessoasService } from "@/services/pessoa.service";
import { useUsuarios } from "@/hooks/usuario/useUsuarios";
import type { ApiError } from "@/types/api.types";
import {
  rotuloRole,
  varianteRole,
  formatarUltimoAcesso,
} from "@/lib/formats/usuarioFormat";
import { Avatar } from "@/components/common/Avatar/Avatar";
import { MenuAcoes, ItemAcao } from "@/components/common/menuacoes/MenuAcoes";
import styles from "./usuarios.module.css";
import { UsuarioResponse } from "@/types/usuario.types";
import { ModalStatusUsuario } from "./(editar)/ModalStatusUsuario";
import { ModalPermissaoUsuario } from "./(editar)/ModalPermissaoUsuario";
import { ModalArquivarUsuario } from "./(arquivarusuario)/ModalArquivarUsuario";
import { useBuscaUrl } from "@/hooks/busca/useBuscaUrl";
import { useAuthStore } from "@/store/authStore";
import { AcessoRestrito } from "@/components/common/AcessoRestrito/AcessoRestrito";
import { EstadoVazio } from "@/components/common/EstadoVazio/EstadoVazio";
import { SearchX, Inbox } from 'lucide-react'
import { SkeletonUsuarios } from "./SkeletonUsuarios";
import { EstadoErro } from "@/components/common/EstadoErro/EstadoErro";
import { podeGerenciarUsuarios } from "@/lib/permissoes";
import { urlFoto } from "@/lib/urlFoto";

const TAMANHO_PAGINA = 10;

function CabecalhoTabela() {
  return (
    <thead>
      <tr>
        <th>Usuário</th>
        <th>E-mail</th>
        <th>Perfil</th>
        <th>Status</th>
        <th>Último acesso</th>
        <th className={styles.colunaAcoes}>Ações</th>
      </tr>
    </thead>
  )
}

function UsuariosConteudo() {
  const { busca, setBusca, buscaDebounced } = useBuscaUrl({ delay: 250 })
  const [pagina, setPagina] = useState(0)
  const [usuarioStatus, setUsuarioStatus] = useState<UsuarioResponse | null>(null)
  const [usuarioPermissao, setUsuarioPermissao] = useState<UsuarioResponse | null>(null)
  const [usuarioArquivando, setUsuarioArquivando] = useState<UsuarioResponse | null>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState<string | null>(null)
  const queryClient = useQueryClient()

  async function reenviarConvite(u: UsuarioResponse) {
    try {
      await pessoasService.reenviarConvite(u.id)
      invalidarCache(queryClient, 'usuario')
      notificar.sucesso(`Convite reenviado para ${u.nome}.`)
    } catch (error: unknown) {
      // A5/rodada 3: o backend agora recusa reenviar convite a usuário desativado — a
      // mensagem dele precisa aparecer de verdade, não um texto genérico.
      if (axios.isAxiosError<ApiError>(error)) {
        notificar.erro(error.response?.data?.message ?? 'Não foi possível reenviar o convite.')
      } else {
        notificar.erro('Não foi possível reenviar o convite.')
      }
    }
  }

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeGerenciarUsuarios(role)

  const { data, isLoading, isError, isFetching, refetch } = useUsuarios({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
    enabled: autorizado,
  })

  // A ordem (ativos primeiro, depois nome) vem PRONTA do banco — ver
  // UsuarioRepository.buscarPorIgreja. Reordenar aqui só arrumaria a página aberta e deixaria
  // o conjunto errado entre páginas.
  const usuarios = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.containerTabela}>
          <table className={styles.tabela}>
            <CabecalhoTabela />
            <tbody>
              <SkeletonUsuarios linhas={TAMANHO_PAGINA} />
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Usuários</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>Usuários</h1>
            {totalElementos > 0 && <span className={styles.contador}>{totalElementos}</span>}
          </div>
          <p className={styles.subtitulo}>Pessoas com acesso ao sistema</p>
        </div>
      </header>

      <div className={styles.barraBusca}>
        <input
          type="text"
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar por nome ou e-mail..."
          className={styles.inputBusca}
        />
        {isFetching && <span className={styles.indicadorAtualizando}>Atualizando…</span>}
      </div>

      <div className={styles.containerTabela}>
        <table className={styles.tabela}>
          <CabecalhoTabela />
          <tbody>
            {isLoading ? (
              <SkeletonUsuarios linhas={TAMANHO_PAGINA} />
            ) : isError ? (
              <tr><td colSpan={6}>
                <EstadoErro
                  titulo="Não foi possível carregar os usuários"
                  mensagem="Verifique sua conexão e tente novamente."
                  aoTentarNovamente={() => refetch()}
                />
              </td></tr>
            ) : usuarios.length === 0 ? (
              <tr>
                <td colSpan={6}>
                  <EstadoVazio
                    icone={buscaDebounced ? SearchX : Inbox}
                    titulo={buscaDebounced ? `Nenhum usuário encontrado` : 'Nenhum usuário cadastrado'}
                    mensagem={
                      buscaDebounced
                        ? `Não encontramos resultados para "${buscaDebounced}". Tente outro termo.`
                        : 'Comece cadastrando o primeiro usuário da sua igreja.'
                    }
                    acaoSecundaria={buscaDebounced ? { label: 'Limpar busca', onClick: () => setBusca('') } : undefined}
                  />
                </td>
              </tr>
            ) : (
              usuarios.map((u) => {
                // A5/rodada 3: usuário desativado não recebe convite — some a ação em vez
                // de deixar clicar e falhar (o backend recusaria com USUARIO_DESATIVADO).
                const acoes: ItemAcao[] = [
                  ...(u.convitePendente && u.ativo
                    ? [{ label: "Reenviar convite", icone: Send, onClick: () => reenviarConvite(u) }]
                    : []),
                  { label: "Alterar perfil", icone: Shield, onClick: () => setUsuarioPermissao(u) },
                  ...(u.ativo
                    ? [{ label: "Desativar acesso", icone: Ban, onClick: () => setUsuarioStatus(u), perigo: true }]
                    : [{ label: "Reativar", icone: UserCheck, onClick: () => setUsuarioStatus(u) }]),
                  { label: "Arquivar", icone: Archive, onClick: () => setUsuarioArquivando(u) },
                ];

                return (
                  <tr key={u.id} className={!u.ativo ? styles.linhaInativa : undefined}>
                    <td>
                      <div className={styles.celulaUsuario}>
                        {u.fotoId ? (
                          <span onClick={() => setFotoVisualizando(u.fotoId!)} style={{ cursor: 'pointer' }}>
                            <Avatar fotoId={u.fotoId} nome={u.nome} tamanho="sm" />
                          </span>
                        ) : (
                          <Avatar fotoId={u.fotoId} nome={u.nome} tamanho="sm" />
                        )}
                        <span className={styles.nome}>{u.nome}</span>
                      </div>
                    </td>
                    <td className={styles.email}>{u.email}</td>
                    <td>
                      <span className={`${styles.badgeRole} ${styles[varianteRole(u.role)]}`}>
                        {rotuloRole(u.role)}
                      </span>
                      {u.capacidadesExtras?.map(c => (
                        <span key={c} className={styles.badgeCapacidade}>
                          {c === 'SECRETARIO' ? 'Secretário' : 'Tesoureiro'}
                        </span>
                      ))}
                    </td>
                    <td>
                      {u.convitePendente ? (
                        <span className={`${styles.status} ${styles.pendente}`} title="Ainda não fez login no sistema">
                          <span className={styles.bolinha} />
                          Convite pendente
                        </span>
                      ) : (
                        <span className={`${styles.status} ${u.ativo ? styles.ativo : styles.inativo}`}>
                          <span className={styles.bolinha} />
                          {u.ativo ? "Ativo" : "Inativo"}
                        </span>
                      )}
                    </td>
                    <td className={styles.ultimoAcesso}>
                      {formatarUltimoAcesso(u.ultimoLoginEm)}
                    </td>
                    <td className={styles.colunaAcoes}>
                      <div className={styles.acoesCell}>
                        <MenuAcoes itens={acoes} />
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>

        {!isLoading && !isError && usuarios.length > 0 && (
          <footer className={styles.rodape}>
            <span className={styles.contagem}>
              Exibindo {usuarios.length} de {totalElementos} usuários
            </span>
            <div className={styles.paginacao}>
              <button
                onClick={() => setPagina((p) => Math.max(0, p - 1))}
                disabled={pagina === 0}
                className={styles.botaoPagina}
              >‹</button>
              <span className={styles.infoPagina}>
                Página {pagina + 1} de {totalPaginas}
              </span>
              <button
                onClick={() => setPagina((p) => p + 1)}
                disabled={pagina + 1 >= totalPaginas}
                className={styles.botaoPagina}
              >›</button>
            </div>
          </footer>
        )}
      </div>
      {usuarioStatus && (
        <ModalStatusUsuario usuario={usuarioStatus} onClose={() => setUsuarioStatus(null)} />
      )}

      {usuarioPermissao && (
        <ModalPermissaoUsuario usuario={usuarioPermissao} onClose={() => setUsuarioPermissao(null)} />
      )}

      {usuarioArquivando && (
        <ModalArquivarUsuario usuario={usuarioArquivando} onClose={() => setUsuarioArquivando(null)} />
      )}

      {fotoVisualizando && (
        <div className={styles.viewerOverlay} onMouseDown={() => setFotoVisualizando(null)}>
          <div className={styles.viewerModal} onMouseDown={e => e.stopPropagation()}>
            <button className={styles.viewerClose} onClick={() => setFotoVisualizando(null)}>
              <X size={20} />
            </button>
            <img src={urlFoto(fotoVisualizando, 'DISPLAY')!} alt="" className={styles.viewerImg} />
          </div>
        </div>
      )}

    </div>
  );
}

export default function UsuariosPage() {
  return (
    <Suspense fallback={<div className={styles.pagina}>Carregando…</div>}>
      <UsuariosConteudo />
    </Suspense>
  )
}