# Domus

SaaS multi-tenant para gestão administrativa de igrejas.
Cobre membros, eventos e controle financeiro com isolamento de dados por tenant.

## Stack

- **Backend:** Java 21 + Spring Boot + PostgreSQL
- **Frontend:** Next.js 14 + Tailwind CSS + shadcn/ui
- **Banco:** PostgreSQL gerenciado (Neon)
- **Infra:** Docker + GitHub Actions + Render

## Pré-requisitos

- Java 21
- Node.js 20+
- Docker
- Conta no [Neon](https://neon.tech) com os databases `domus_dev` e `domus_prod`

## Setup local

1. Clone o repositório
```bash
   git clone https://github.com/seu-usuario/domus.git
   cd domus
```

2. Configure as variáveis de ambiente
```bash
   cp backend/.env.example backend/.env
   cp frontend/.env.example frontend/.env
   # Preencha os valores nos arquivos .env com as credenciais do Neon
```

3. Suba o ambiente
```bash
   docker compose up
```

4. Acesse
   - Frontend: http://localhost:3000
   - Backend: http://localhost:8080

## Documentação

A documentação técnica completa está no Notion do projeto.

## Licença

Proprietária — todos os direitos reservados. Consulte o arquivo LICENSE.