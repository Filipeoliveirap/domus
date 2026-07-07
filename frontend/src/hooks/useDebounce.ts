import { useEffect, useState } from "react";

export function useDebounce<T>(valor: T, delayMs = 350): T {
  const [debounced, setDebounced] = useState(valor);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(valor), delayMs);
    return () => clearTimeout(id);
  }, [valor, delayMs]);

  return debounced;
}