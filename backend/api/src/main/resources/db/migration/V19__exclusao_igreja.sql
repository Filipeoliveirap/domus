ALTER TABLE igreja
  ADD COLUMN exclusao_agendada_em TIMESTAMP,
  ADD COLUMN exclusao_agendada_por_usuario_id UUID REFERENCES usuario(id);
