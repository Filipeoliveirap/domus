CREATE TABLE evento_serie (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id                   UUID NOT NULL REFERENCES igreja(id),
    frequencia                  VARCHAR(10) NOT NULL,
    intervalo                   INTEGER NOT NULL DEFAULT 1 CHECK (intervalo > 0),
    dias_semana                 VARCHAR(80),
    tipo_recorrencia_mensal     VARCHAR(20),
    data_fim                    DATE,
    numero_ocorrencias          INTEGER CHECK (numero_ocorrencias > 0),
    ativa                       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por_usuario_id       UUID REFERENCES usuario(id),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),

    CHECK (data_fim IS NULL OR numero_ocorrencias IS NULL)
);

ALTER TABLE evento ADD COLUMN serie_id UUID REFERENCES evento_serie(id);
ALTER TABLE evento ADD COLUMN diverge_da_serie BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_evento_serie ON evento (serie_id);
