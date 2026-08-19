"use client";

import { Fragment, useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { MoreVertical, LucideIcon } from "lucide-react";
import { useClickFora } from "@/hooks/useClickFora";
import styles from "./MenuAcoes.module.css";

export interface ItemAcao {
  label: string;
  icone: LucideIcon;
  onClick: () => void;
  perigo?: boolean;
  separadorAntes?: boolean;
}

interface MenuAcoesProps {
  itens: ItemAcao[];
}

export function MenuAcoes({ itens }: MenuAcoesProps) {
  const [aberto, setAberto] = useState(false);
  const [posicao, setPosicao] = useState({ top: 0, right: 0 });
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useClickFora(containerRef, () => setAberto(false));

  useEffect(() => {
    if (!aberto) return;
    function aoTeclar(e: KeyboardEvent) {
      if (e.key === "Escape") setAberto(false);
    }
    document.addEventListener("keydown", aoTeclar);
    return () => document.removeEventListener("keydown", aoTeclar);
  }, [aberto]);

  /** Recalcula se o menu não couber abaixo do gatilho — sobe em vez de descer. */
  const recalcularPosicao = useCallback(() => {
    const btn = triggerRef.current;
    const menu = menuRef.current;
    if (!btn || !menu) return;

    const margem = 8;
    const btnRect = btn.getBoundingClientRect();
    const alturaMenu = menu.offsetHeight;
    const larguraMenu = menu.offsetWidth;

    // Clampa: numa tabela larga com scroll horizontal (comum no mobile), o botão pode
    // estar perto ou além da borda direita real da tela — sem isso, "right" vira negativo
    // e o menu vaza pra fora da viewport em vez de encostar na borda.
    const direita = Math.min(
      Math.max(window.innerWidth - btnRect.right, margem),
      window.innerWidth - larguraMenu - margem
    );

    const abaixo = btnRect.bottom + 6;
    const acima = btnRect.top - alturaMenu - 6;

    // Espaço abaixo suficiente ou não cabe em cima — desce
    if (abaixo + alturaMenu <= window.innerHeight - 8 || acima < 8) {
      setPosicao({ top: abaixo, right: direita });
    } else {
      setPosicao({ top: acima, right: direita });
    }
  }, []);

  useLayoutEffect(() => {
    if (aberto) recalcularPosicao();
  }, [aberto, itens, recalcularPosicao]);

  function abrirMenu() {
    const btn = triggerRef.current;
    if (!btn) { setAberto(true); return; }
    const rect = btn.getBoundingClientRect();
    setPosicao({ top: rect.bottom + 6, right: window.innerWidth - rect.right });
    setAberto(true);
  }

  function executar(acao: () => void) {
    acao();
    setAberto(false);
  }

  return (
    <div className={styles.container} ref={containerRef}>
      <button
        ref={triggerRef}
        className={styles.gatilho}
        onClick={abrirMenu}
        aria-haspopup="menu"
        aria-expanded={aberto}
        aria-label="Ações"
      >
        <MoreVertical size={18} />
      </button>

      {aberto && typeof document !== "undefined" &&
        createPortal(
          <div
            ref={menuRef}
            className={styles.menu}
            role="menu"
            style={{
              position: "fixed",
              top: posicao.top,
              right: posicao.right,
            }}
            onMouseDown={(e) => e.stopPropagation()}
          >
            {itens.map((item) => {
              const Icone = item.icone;
              return (
                <Fragment key={item.label}>
                  {item.separadorAntes && <div className={styles.divisor} />}
                  <button
                    className={`${styles.item} ${item.perigo ? styles.itemPerigo : ""}`}
                    role="menuitem"
                    onClick={() => executar(item.onClick)}
                  >
                    <Icone size={16} />
                    <span>{item.label}</span>
                  </button>
                </Fragment>
              );
            })}
          </div>,
          document.body
        )}
    </div>
  );
}