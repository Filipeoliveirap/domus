import { useEffect, RefObject } from "react";

export function useClickFora<T extends HTMLElement>(
  ref: RefObject<T | null>,
  aoClicarFora: () => void
) {
  useEffect(() => {
    function handler(evento: MouseEvent) {
      if (ref.current && !ref.current.contains(evento.target as Node)) {
        aoClicarFora();
      }
    }
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [ref, aoClicarFora]);
}