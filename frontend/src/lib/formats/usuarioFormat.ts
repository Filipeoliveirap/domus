const ROTULOS_ROLE: Record<string, string> = {
  ADMIN_IGREJA: "Administrador",
  LIDER: "Líder",
  ACESSO_COMUM: "Acesso comum",
};

export function rotuloRole(role: string): string {
  return ROTULOS_ROLE[role] ?? role;
}

export function varianteRole(role: string): "admin" | "lider" | "comum" | "outro" {
  if (role === "ADMIN_IGREJA") return "admin";
  if (role === "LIDER") return "lider";
  if (role === "ACESSO_COMUM") return "comum";
  return "outro";
}

export function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
  return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
}

export function formatarUltimoAcesso(iso: string | null): string {
  if (!iso) return "Nunca";
  const data = new Date(iso);
  const agora = new Date();
  const hora = new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(data);

  if (data.toDateString() === agora.toDateString()) return `Hoje, ${hora}`;

  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (data.toDateString() === ontem.toDateString()) return `Ontem, ${hora}`;

  const mesmoAno = data.getFullYear() === agora.getFullYear();
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "short",
    ...(mesmoAno ? {} : { year: "numeric" }),
  }).format(data);
}