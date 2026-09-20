// Componente de exemplo pra teste. Não é usado em produção — serve de modelo
// pra outros componentes do projeto. Quando alguém for testar ListaPessoas
// real, copia o shape daqui.

import { useQuery } from "@tanstack/react-query";

type Pessoa = {
  id: string;
  nome: string;
  apelido?: string;
};

async function buscarPessoas(): Promise<Pessoa[]> {
  const resposta = await fetch("/api/pessoas");
  if (!resposta.ok) throw new Error("Falha ao buscar pessoas");
  return resposta.json();
}

export function ListaPessoasExemplo() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["pessoas", "exemplo"],
    queryFn: buscarPessoas,
  });

  if (isLoading) return <div role="status">Carregando pessoas…</div>;
  if (error) return <div role="alert">Erro ao carregar pessoas.</div>;
  if (!data || data.length === 0)
    return <div>Nenhuma pessoa cadastrada ainda.</div>;

  return (
    <ul aria-label="Lista de pessoas">
      {data.map((p) => (
        <li key={p.id}>
          {p.apelido && <strong>{p.apelido}</strong>}
          {" — "}
          {p.nome}
        </li>
      ))}
    </ul>
  );
}
