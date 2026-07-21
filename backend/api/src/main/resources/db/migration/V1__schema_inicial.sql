-- V1: schema inicial do Domus.
--
-- Consolida as antigas V1-V16 numa migration so. Feito em 2026-07-21, junto com a
-- renomeacao membro->pessoa: nao havia dado real ainda, e uma migration de RENAME
-- carregaria para sempre a cicatriz do nome antigo (constraints chamadas
-- fk_usuario_membro numa tabela chamada pessoa).
--
-- pessoa.vinculo substitui o antigo status (ATIVO|INATIVO|VISITANTE) + batizado:
--   MEMBRO      = batizado, formalmente membro da igreja
--   CONGREGANTE = frequenta, nao e batizado (absorveu VISITANTE)
--
-- "Inativo" nao e estado: quem parou de frequentar e ARQUIVADO (deleted_at), que e o
-- mesmo mecanismo usado por todo o resto do sistema. Manter um segundo eixo so para
-- isso seria duplicar o soft delete.
--
-- O boolean `batizado` saiu por ser redundante: MEMBRO ja significa batizado, e manter
-- os dois permitia o estado impossivel "membro nao batizado".
--
-- Gerado a partir do schema real (pg_dump --schema-only) e transformado, em vez de
-- reescrito a mao — 16 migrations reescritas de memoria perderiam algum indice ou CHECK
-- em silencio.

--


-- Dumped from database version 16.14 (b253d90)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg13+1)

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- (o dump traz CREATE SCHEMA public; removido: o Flyway roda DENTRO do schema, que ja existe)

--
-- Name: valida_hierarquia_igreja(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.valida_hierarquia_igreja() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Virar independente (ou continuar) nunca viola nada.
    IF NEW.igreja_mae_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.igreja_mae_id = NEW.id THEN
        RAISE EXCEPTION 'Igreja % nao pode ser mae de si mesma', NEW.id
            USING ERRCODE = 'check_violation';
    END IF;

    -- FOR UPDATE, e não um SELECT simples, é o que faz esta checagem valer sob concorrência.
    -- Em READ COMMITTED, duas transações simultâneas leriam o estado antigo uma da outra e as
    -- DUAS passariam num SELECT comum — formando exatamente o ciclo que queremos impedir.
    -- Travando a linha da mãe, elas serializam. No caso patológico (A→B e B→A ao mesmo tempo)
    -- o Postgres detecta deadlock e aborta uma das duas: falha segura, que é o que queremos.
    -- O caminho normal da aplicação nunca chega aqui em conflito, porque o VinculoService já
    -- trava as duas linhas em ordem de UUID antes de validar.
    PERFORM 1 FROM igreja WHERE id = NEW.igreja_mae_id FOR UPDATE;

    IF EXISTS (
        SELECT 1 FROM igreja mae
        WHERE mae.id = NEW.igreja_mae_id
          AND mae.igreja_mae_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Igreja % ja e congregacao e nao pode ter congregacoes (regra dos 2 niveis)',
            NEW.igreja_mae_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF EXISTS (
        SELECT 1 FROM igreja filha
        WHERE filha.igreja_mae_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Igreja % tem congregacoes e nao pode virar congregacao (regra dos 2 niveis)',
            NEW.id
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$;

--
-- Name: acompanhante_inscricao; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acompanhante_inscricao (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inscricao_id uuid NOT NULL,
    nome character varying(255) NOT NULL,
    telefone character varying(20),
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

--
-- Name: TABLE acompanhante_inscricao; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.acompanhante_inscricao IS 'Só para quem NÃO é membro da igreja. Existe para responder, ao ler a lista, "de onde veio essa pessoa que ninguém conhece".';

--
-- Name: categoria_financeira; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categoria_financeira (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    igreja_id uuid NOT NULL,
    nome character varying(255) NOT NULL,
    tipo character varying(20) NOT NULL,
    deleted_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_categoria_tipo CHECK (((tipo)::text = ANY ((ARRAY['ENTRADA'::character varying, 'SAIDA'::character varying, 'AMBOS'::character varying])::text[])))
);

--
-- Name: evento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.evento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    igreja_id uuid NOT NULL,
    titulo character varying(255) NOT NULL,
    descricao text,
    inicio_em timestamp without time zone NOT NULL,
    fim_em timestamp without time zone,
    local character varying(255),
    foto character varying(500),
    deleted_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    vagas integer,
    preco numeric(10,2),
    exclusivo_membros boolean DEFAULT false NOT NULL,
    requer_inscricao boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_evento_preco_positivo CHECK (((preco IS NULL) OR (preco > (0)::numeric))),
    CONSTRAINT chk_evento_vagas_positivas CHECK (((vagas IS NULL) OR (vagas > 0)))
);

--
-- Name: igreja; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.igreja (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome character varying(255) NOT NULL,
    cnpj character varying(18),
    email character varying(255) NOT NULL,
    telefone character varying(20),
    plano character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    igreja_mae_id uuid,
    codigo_vinculo character varying(9),
    cep character varying(9),
    logradouro character varying(255),
    numero character varying(20),
    complemento character varying(255),
    bairro character varying(255),
    cidade character varying(255),
    uf character(2),
    logo_url character varying(500),
    razao_social character varying(255),
    denominacao character varying(255),
    atualizado_por_usuario_id uuid,
    vinculado_em timestamp without time zone,
    vinculado_por_usuario_id uuid,
    codigo_gerado_em timestamp without time zone
);

--
-- Name: inscricao_evento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inscricao_evento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    igreja_id uuid NOT NULL,
    evento_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    inscrito_por_usuario_id uuid,
    status character varying(20) DEFAULT 'CONFIRMADA'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--
-- Name: COLUMN inscricao_evento.inscrito_por_usuario_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.inscricao_evento.inscrito_por_usuario_id IS 'NULL = a própria pessoa se inscreveu. Preenchido = alguém a inscreveu.';

--
-- Name: pessoa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pessoa (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    igreja_id uuid NOT NULL,
    nome character varying(255) NOT NULL,
    email character varying(255),
    telefone character varying(20),
    data_nascimento date,
    estado_civil character varying(20),
    ministerio character varying(255),
    foto character varying(500),
    observacoes text,
    deleted_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    cep character varying(9),
    logradouro character varying(255),
    numero character varying(20),
    complemento character varying(255),
    bairro character varying(255),
    cidade character varying(255),
    uf character(2),
    data_batismo date,
    vinculo character varying(20) DEFAULT 'CONGREGANTE'::character varying NOT NULL
);

--
-- Name: movimentacao_financeira; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.movimentacao_financeira (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    igreja_id uuid NOT NULL,
    categoria_id uuid NOT NULL,
    criado_por_usuario_id uuid NOT NULL,
    atualizado_por_usuario_id uuid,
    pessoa_id uuid,
    tipo character varying(20) NOT NULL,
    valor numeric(15,2) NOT NULL,
    data_movimentacao date NOT NULL,
    descricao text,
    deleted_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_movimentacao_tipo CHECK (((tipo)::text = ANY ((ARRAY['ENTRADA'::character varying, 'SAIDA'::character varying])::text[]))),
    CONSTRAINT chk_movimentacao_valor CHECK ((valor > (0)::numeric))
);

--
-- Name: outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tipo_entidade character varying(30) NOT NULL,
    tipo_evento character varying(20) NOT NULL,
    entidade_id uuid NOT NULL,
    igreja_id uuid NOT NULL,
    processado boolean DEFAULT false NOT NULL,
    tentativas integer DEFAULT 0 NOT NULL,
    erro text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    processado_at timestamp without time zone,
    CONSTRAINT chk_outbox_tipo_entidade CHECK (((tipo_entidade)::text = ANY ((ARRAY['PESSOA'::character varying, 'EVENTO'::character varying, 'USUARIO'::character varying, 'MOVIMENTACAO'::character varying, 'CATEGORIA'::character varying])::text[]))),
    CONSTRAINT chk_outbox_tipo_evento CHECK (((tipo_evento)::text = ANY ((ARRAY['CRIADO'::character varying, 'ATUALIZADO'::character varying, 'REMOVIDO'::character varying])::text[])))
);

--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nome character varying(50) NOT NULL,
    descricao character varying(255),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);

--
-- Name: usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    igreja_id uuid NOT NULL,
    pessoa_id uuid NOT NULL,
    role_id uuid NOT NULL,
    senha_hash character varying(255),
    ativo boolean DEFAULT true NOT NULL,
    ultimo_login_em timestamp without time zone,
    delete_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    google_sub character varying(255)
);

--
-- Name: acompanhante_inscricao acompanhante_inscricao_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acompanhante_inscricao
    ADD CONSTRAINT acompanhante_inscricao_pkey PRIMARY KEY (id);

--
-- Name: categoria_financeira categoria_financeira_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria_financeira
    ADD CONSTRAINT categoria_financeira_pkey PRIMARY KEY (id);

--
-- Name: igreja igreja_codigo_vinculo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.igreja
    ADD CONSTRAINT igreja_codigo_vinculo_key UNIQUE (codigo_vinculo);

--
-- Name: inscricao_evento inscricao_evento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_evento
    ADD CONSTRAINT inscricao_evento_pkey PRIMARY KEY (id);

--
-- Name: movimentacao_financeira movimentacao_financeira_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimentacao_financeira
    ADD CONSTRAINT movimentacao_financeira_pkey PRIMARY KEY (id);

--
-- Name: outbox outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox
    ADD CONSTRAINT outbox_pkey PRIMARY KEY (id);

--
-- Name: evento pk_evento; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT pk_evento PRIMARY KEY (id);

--
-- Name: igreja pk_igreja; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.igreja
    ADD CONSTRAINT pk_igreja PRIMARY KEY (id);

--
-- Name: pessoa pk_pessoa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT pk_pessoa PRIMARY KEY (id);

--
-- Name: role pk_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT pk_role PRIMARY KEY (id);

--
-- Name: usuario pk_usuario; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT pk_usuario PRIMARY KEY (id);

--
-- Name: inscricao_evento uk_inscricao_evento_pessoa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_evento
    ADD CONSTRAINT uk_inscricao_evento_pessoa UNIQUE (evento_id, pessoa_id);

--
-- Name: role uq_role_nome; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT uq_role_nome UNIQUE (nome);

--
-- Name: usuario uq_usuario_pessoa; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT uq_usuario_pessoa UNIQUE (pessoa_id);

--
-- Name: idx_acompanhante_inscricao_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_acompanhante_inscricao_id ON public.acompanhante_inscricao USING btree (inscricao_id);

--
-- Name: idx_categoria_igreja; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categoria_igreja ON public.categoria_financeira USING btree (igreja_id);

--
-- Name: idx_evento_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_deleted_at ON public.evento USING btree (deleted_at);

--
-- Name: idx_evento_igreja_inicio; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_evento_igreja_inicio ON public.evento USING btree (igreja_id, inicio_em);

--
-- Name: idx_igreja_mae_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_igreja_mae_id ON public.igreja USING btree (igreja_mae_id) WHERE (igreja_mae_id IS NOT NULL);

--
-- Name: idx_inscricao_evento_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inscricao_evento_id ON public.inscricao_evento USING btree (evento_id);

--
-- Name: idx_inscricao_pessoa_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inscricao_pessoa_id ON public.inscricao_evento USING btree (pessoa_id);

--
-- Name: idx_pessoa_deleted_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_deleted_at ON public.pessoa USING btree (deleted_at);

--
-- Name: idx_pessoa_igreja_nome; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_igreja_nome ON public.pessoa USING btree (igreja_id, nome);

--
-- Name: idx_pessoa_igreja_vinculo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_pessoa_igreja_vinculo ON public.pessoa USING btree (igreja_id, vinculo);

--
-- Name: idx_movimentacao_igreja_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimentacao_igreja_categoria ON public.movimentacao_financeira USING btree (igreja_id, categoria_id);

--
-- Name: idx_movimentacao_igreja_data; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimentacao_igreja_data ON public.movimentacao_financeira USING btree (igreja_id, data_movimentacao);

--
-- Name: idx_movimentacao_igreja_pessoa; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movimentacao_igreja_pessoa ON public.movimentacao_financeira USING btree (igreja_id, pessoa_id);

--
-- Name: idx_outbox_pendentes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_pendentes ON public.outbox USING btree (processado, created_at) WHERE (processado = false);

--
-- Name: idx_usuario_igreja_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_igreja_id ON public.usuario USING btree (igreja_id);

--
-- Name: uq_categoria_nome_igreja; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_categoria_nome_igreja ON public.categoria_financeira USING btree (igreja_id, lower((nome)::text)) WHERE (deleted_at IS NULL);

--
-- Name: uq_pessoa_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_pessoa_email ON public.pessoa USING btree (email) WHERE (email IS NOT NULL);

--
-- Name: ux_usuario_google_sub; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_usuario_google_sub ON public.usuario USING btree (google_sub);

--
-- Name: igreja trg_igreja_hierarquia; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_igreja_hierarquia BEFORE INSERT OR UPDATE OF igreja_mae_id ON public.igreja FOR EACH ROW EXECUTE FUNCTION public.valida_hierarquia_igreja();

--
-- Name: acompanhante_inscricao acompanhante_inscricao_inscricao_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acompanhante_inscricao
    ADD CONSTRAINT acompanhante_inscricao_inscricao_id_fkey FOREIGN KEY (inscricao_id) REFERENCES public.inscricao_evento(id) ON DELETE CASCADE;

--
-- Name: categoria_financeira categoria_financeira_igreja_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria_financeira
    ADD CONSTRAINT categoria_financeira_igreja_id_fkey FOREIGN KEY (igreja_id) REFERENCES public.igreja(id);

--
-- Name: evento fk_evento_igreja; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.evento
    ADD CONSTRAINT fk_evento_igreja FOREIGN KEY (igreja_id) REFERENCES public.igreja(id) ON DELETE CASCADE;

--
-- Name: pessoa fk_pessoa_igreja; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT fk_pessoa_igreja FOREIGN KEY (igreja_id) REFERENCES public.igreja(id) ON DELETE CASCADE;

--
-- Name: usuario fk_usuario_igreja; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fk_usuario_igreja FOREIGN KEY (igreja_id) REFERENCES public.igreja(id) ON DELETE CASCADE;

--
-- Name: usuario fk_usuario_pessoa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fk_usuario_pessoa FOREIGN KEY (pessoa_id) REFERENCES public.pessoa(id);

--
-- Name: usuario fk_usuario_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fk_usuario_role FOREIGN KEY (role_id) REFERENCES public.role(id);

--
-- Name: igreja igreja_atualizado_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.igreja
    ADD CONSTRAINT igreja_atualizado_por_usuario_id_fkey FOREIGN KEY (atualizado_por_usuario_id) REFERENCES public.usuario(id);

--
-- Name: igreja igreja_igreja_mae_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.igreja
    ADD CONSTRAINT igreja_igreja_mae_id_fkey FOREIGN KEY (igreja_mae_id) REFERENCES public.igreja(id);

--
-- Name: igreja igreja_vinculado_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.igreja
    ADD CONSTRAINT igreja_vinculado_por_usuario_id_fkey FOREIGN KEY (vinculado_por_usuario_id) REFERENCES public.usuario(id);

--
-- Name: inscricao_evento inscricao_evento_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_evento
    ADD CONSTRAINT inscricao_evento_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.evento(id);

--
-- Name: inscricao_evento inscricao_evento_igreja_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_evento
    ADD CONSTRAINT inscricao_evento_igreja_id_fkey FOREIGN KEY (igreja_id) REFERENCES public.igreja(id);

--
-- Name: inscricao_evento inscricao_evento_inscrito_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_evento
    ADD CONSTRAINT inscricao_evento_inscrito_por_usuario_id_fkey FOREIGN KEY (inscrito_por_usuario_id) REFERENCES public.usuario(id);

--
-- Name: inscricao_evento inscricao_evento_pessoa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inscricao_evento
    ADD CONSTRAINT inscricao_evento_pessoa_id_fkey FOREIGN KEY (pessoa_id) REFERENCES public.pessoa(id);

--
-- Name: movimentacao_financeira movimentacao_financeira_atualizado_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimentacao_financeira
    ADD CONSTRAINT movimentacao_financeira_atualizado_por_usuario_id_fkey FOREIGN KEY (atualizado_por_usuario_id) REFERENCES public.usuario(id);

--
-- Name: movimentacao_financeira movimentacao_financeira_categoria_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimentacao_financeira
    ADD CONSTRAINT movimentacao_financeira_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES public.categoria_financeira(id);

--
-- Name: movimentacao_financeira movimentacao_financeira_criado_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimentacao_financeira
    ADD CONSTRAINT movimentacao_financeira_criado_por_usuario_id_fkey FOREIGN KEY (criado_por_usuario_id) REFERENCES public.usuario(id);

--
-- Name: movimentacao_financeira movimentacao_financeira_igreja_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimentacao_financeira
    ADD CONSTRAINT movimentacao_financeira_igreja_id_fkey FOREIGN KEY (igreja_id) REFERENCES public.igreja(id);

--
-- Name: movimentacao_financeira movimentacao_financeira_pessoa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.movimentacao_financeira
    ADD CONSTRAINT movimentacao_financeira_pessoa_id_fkey FOREIGN KEY (pessoa_id) REFERENCES public.pessoa(id);

--


--
-- Name: pessoa chk_pessoa_vinculo; Type: CHECK CONSTRAINT; Schema: public
-- O enum vive na aplicacao (Vinculo.java); o CHECK impede que um caminho que nao passe
-- por ela (script, correcao manual) grave um valor que a aplicacao nao sabe ler.
--

ALTER TABLE ONLY public.pessoa
    ADD CONSTRAINT chk_pessoa_vinculo CHECK (vinculo IN ('MEMBRO', 'CONGREGANTE'));


--
-- Perfis de acesso. ACESSO_COMUM substitui o antigo MEMBRO: o nome fala de NIVEL DE
-- ACESSO, nao de vinculo com a igreja — um congregante com login tem acesso comum, e
-- chama-lo de "membro" era a confusao que esta mudanca elimina.
--

INSERT INTO public.role (nome, descricao) VALUES
    ('ADMIN_IGREJA', 'Acesso total a todos os modulos da igreja'),
    ('LIDER',        'Acesso de leitura e escrita em pessoas e eventos'),
    ('ACESSO_COMUM', 'Acesso comum: ve pessoas e eventos, e se inscreve. Sem gestao.');
