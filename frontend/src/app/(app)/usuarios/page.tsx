"use client";

import { useState } from "react";
import Link from "next/link";
import { ChevronRight, Shield, Ban, Archive, UserCheck } from "lucide-react";
import { useDebounce } from "@/hooks/useDebounce";
import { useUsuarios } from "@/hooks/usuario/useUsuarios";
import {
  iniciais,
  rotuloRole,
  varianteRole,
  formatarUltimoAcesso,
} from "@/lib/formats/usuarioFormat";
import { MenuAcoes, ItemAcao } from "@/components/common/menuacoes/MenuAcoes";
import styles from "./usuarios.module.css";
import { UsuarioResponse } from "@/types/usuario.types";
import { ModalStatusUsuario } from "./(editar)/ModalStatusUsuario";
import { ModalPermissaoUsuario } from "./(editar)/ModalPermissaoUsuario";
import { ModalArquivarUsuario } from "./(arquivarusuario)/ModalArquivarUsuario";


const TAMANHO_PAGINA = 10;

export default function UsuariosPage() {
  const [busca, setBusca] = useState("");
  const [pagina, setPagina] = useState(0);
  const buscaDebounced = useDebounce(busca, 350);
  const [usuarioStatus, setUsuarioStatus] = useState<UsuarioResponse | null>(null)
  const [usuarioPermissao, setUsuarioPermissao] = useState<UsuarioResponse | null>(null)
  const [usuarioArquivando, setUsuarioArquivando] = useState<UsuarioResponse | null>(null)

  const { data, isLoading, isError, isFetching } = useUsuarios({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
  });

  const usuarios = data?.content ?? [];
  const totalPaginas = data?.totalPages ?? 0;
  const totalElementos = data?.totalElements ?? 0;

  function aoBuscar(valor: string) {
    setBusca(valor);
    setPagina(0);
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
          <h1 className={styles.titulo}>Usuários</h1>
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
          <tbody>
            {isLoading ? (
              <tr><td colSpan={6} className={styles.estadoVazio}>Carregando…</td></tr>
            ) : isError ? (
              <tr><td colSpan={6} className={styles.estadoErro}>
                Não foi possível carregar os usuários. Tente novamente.
              </td></tr>
            ) : usuarios.length === 0 ? (
              <tr><td colSpan={6} className={styles.estadoVazio}>
                {buscaDebounced
                  ? `Nenhum usuário encontrado para "${buscaDebounced}".`
                  : "Nenhum usuário cadastrado ainda."}
              </td></tr>
            ) : (
              usuarios.map((u) => {
                const acoes: ItemAcao[] = [
                  { label: "Alterar perfil", icone: Shield, onClick: () => setUsuarioPermissao(u) },
                  ...(u.ativo
                    ? [{ label: "Desativar acesso", icone: Ban, onClick: () => setUsuarioStatus(u), perigo: true }]
                    : []),
                  { label: "Arquivar", icone: Archive, onClick: () => setUsuarioArquivando(u) },
                ];

                return (
                  <tr key={u.id} className={!u.ativo ? styles.linhaInativa : undefined}>
                    <td>
                      <div className={styles.celulaUsuario}>
                        <span className={styles.avatar}>{iniciais(u.nome)}</span>
                        <span className={styles.nome}>{u.nome}</span>
                      </div>
                    </td>
                    <td className={styles.email}>{u.email}</td>
                    <td>
                      <span className={`${styles.badgeRole} ${styles[varianteRole(u.role)]}`}>
                        {rotuloRole(u.role)}
                      </span>
                    </td>
                    <td>
                      <span className={`${styles.status} ${u.ativo ? styles.ativo : styles.inativo}`}>
                        <span className={styles.bolinha} />
                        {u.ativo ? "Ativo" : "Inativo"}
                      </span>
                    </td>
                    <td className={styles.ultimoAcesso}>
                      {formatarUltimoAcesso(u.ultimoLoginEm)}
                    </td>
                    <td className={styles.colunaAcoes}>
                      <div className={styles.acoesCell}>
                        {!u.ativo && (
                          <button className={styles.btnReativar} onClick={() => setUsuarioStatus(u)}>
                            <UserCheck size={14} /> Reativar
                          </button>
                        )}
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

    </div>
  );
}