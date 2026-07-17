import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { usuarioService } from "@/services/usuarios.service";

interface UseUsuariosParams {
  q: string;
  page: number;
  size?: number;
  sort?: string;
  enabled?: boolean;
}

export function useUsuarios({ q, page, size = 20, sort = "nome,asc", enabled = true }: UseUsuariosParams) {
  return useQuery({
    queryKey: ["usuarios", { q, page, size, sort }],
    queryFn: () => usuarioService.listarUsuarios({ q, page, size, sort }),
    placeholderData: keepPreviousData,
    enabled,
  });
}