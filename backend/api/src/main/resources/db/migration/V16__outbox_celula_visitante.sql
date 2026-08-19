ALTER TABLE outbox DROP CONSTRAINT chk_outbox_tipo_entidade;

ALTER TABLE outbox ADD CONSTRAINT chk_outbox_tipo_entidade
    CHECK (((tipo_entidade)::text = ANY ((ARRAY[
        'PESSOA'::character varying,
        'EVENTO'::character varying,
        'USUARIO'::character varying,
        'MOVIMENTACAO'::character varying,
        'CATEGORIA'::character varying,
        'CELULA'::character varying,
        'VISITANTE'::character varying
    ])::text[])));
