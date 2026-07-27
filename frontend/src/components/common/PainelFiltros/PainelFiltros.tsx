"use client";

import { useRef, useState } from "react";
import { Filter, X } from "lucide-react";
import { useClickFora } from "@/hooks/useClickFora";
import styles from "./PainelFiltros.module.css";

export interface OpcaoFiltro {
  valor: string;
  label: string;
}

export interface GrupoFiltro {
  chave: string;
  titulo: string;
  opcoes: OpcaoFiltro[];
}

interface PainelFiltrosProps {
  grupos: GrupoFiltro[];
  valores: Record<string, string>;
  onAplicar: (valores: Record<string, string>) => void;
}

export function PainelFiltros({ grupos, valores, onAplicar }: PainelFiltrosProps) {
  const [aberto, setAberto] = useState(false);
  const [rascunho, setRascunho] = useState<Record<string, string>>(valores);
  const containerRef = useRef<HTMLDivElement>(null);

  useClickFora(containerRef, () => setAberto(false));

  const totalAtivos = Object.values(valores).filter(Boolean).length;

  function abrir() {
    setRascunho(valores);
    setAberto(true);
  }

  function selecionar(chave: string, valor: string) {
    setRascunho((prev) => ({ ...prev, [chave]: prev[chave] === valor ? "" : valor }));
  }

  function aplicar() {
    onAplicar(rascunho);
    setAberto(false);
  }

  function limpar() {
    const vazio: Record<string, string> = {};
    for (const grupo of grupos) vazio[grupo.chave] = "";
    setRascunho(vazio);
    onAplicar(vazio);
    setAberto(false);
  }

  return (
    <div className={styles.container} ref={containerRef}>
      <button
        type="button"
        className={styles.gatilho}
        onClick={() => (aberto ? setAberto(false) : abrir())}
        aria-haspopup="dialog"
        aria-expanded={aberto}
      >
        <Filter size={16} />
        <span>Filtros</span>
        {totalAtivos > 0 && <span className={styles.contador}>{totalAtivos}</span>}
      </button>

      {aberto && <div className={styles.sobreposicao} onClick={() => setAberto(false)} />}

      {aberto && (
        <div className={styles.painel} role="dialog" aria-label="Filtros">
          <div className={styles.cabecalhoPainel}>
            <span className={styles.tituloPainel}>Filtros</span>
            <button
              type="button"
              className={styles.fechar}
              onClick={() => setAberto(false)}
              aria-label="Fechar filtros"
            >
              <X size={18} />
            </button>
          </div>

          <div className={styles.corpo}>
            {grupos.map((grupo) => (
              <div key={grupo.chave} className={styles.grupo}>
                <span className={styles.tituloGrupo}>{grupo.titulo}</span>
                <div className={styles.opcoes}>
                  {grupo.opcoes.map((opcao) => {
                    const ativa = rascunho[grupo.chave] === opcao.valor;
                    return (
                      <button
                        key={opcao.valor}
                        type="button"
                        className={`${styles.opcao} ${ativa ? styles.opcaoAtiva : ""}`}
                        onClick={() => selecionar(grupo.chave, opcao.valor)}
                        aria-pressed={ativa}
                      >
                        {opcao.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>

          <div className={styles.rodapePainel}>
            <button type="button" className={styles.botaoLimpar} onClick={limpar}>
              Limpar filtros
            </button>
            <button type="button" className={styles.botaoAplicar} onClick={aplicar}>
              Aplicar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
