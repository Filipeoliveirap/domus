ALTER TABLE ministerio ADD COLUMN foto_id UUID REFERENCES foto(id);
ALTER TABLE celula ADD COLUMN foto_id UUID REFERENCES foto(id);
CREATE INDEX ix_ministerio_foto ON ministerio (foto_id);
CREATE INDEX ix_celula_foto ON celula (foto_id);
