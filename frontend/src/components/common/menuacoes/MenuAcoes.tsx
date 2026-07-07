"use client";

import { Fragment, useEffect, useRef, useState } from "react";
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
  const containerRef = useRef<HTMLDivElement>(null);

  useClickFora(containerRef, () => setAberto(false));

  useEffect(() => {
    if (!aberto) return;
    function aoTeclar(e: KeyboardEvent) {
      if (e.key === "Escape") setAberto(false);
    }
    document.addEventListener("keydown", aoTeclar);
    return () => document.removeEventListener("keydown", aoTeclar);
  }, [aberto]);

  function executar(acao: () => void) {
    acao();
    setAberto(false);
  }

  return (
    <div className={styles.container} ref={containerRef}>
      <button
        className={styles.gatilho}
        onClick={() => setAberto((a) => !a)}
        aria-haspopup="menu"
        aria-expanded={aberto}
        aria-label="Ações"
      >
        <MoreVertical size={18} />
      </button>

      {aberto && (
        <div className={styles.menu} role="menu">
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
        </div>
      )}
    </div>
  );
}