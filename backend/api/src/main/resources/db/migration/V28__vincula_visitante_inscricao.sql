-- Convidado sem cadastro (ver V26) trazido do modal "Inscrever alguém" (aba Visitantes) vira
-- só texto solto hoje (nome_convidado/telefone_convidado) — sem ligação de volta pro registro
-- de Visitante que a pessoa escolheu na busca. Sem esse vínculo, duplicidade só dá pra checar
-- comparando nome/telefone normalizados (frágil) e não dá pra saber, na busca de visitantes,
-- quem já está inscrito neste evento pra mostrar bloqueado (mesmo padrão de "Pessoas da
-- igreja"). NULL quando o convidado veio da aba "Pessoa de fora" (sem Visitante nenhum por
-- trás) ou do convite público — ON DELETE SET NULL: apagar o Visitante não pode apagar
-- histórico de inscrição, só desfaz o vínculo (mesmo padrão de convidado_por_pessoa_id, V26).
ALTER TABLE inscricao_evento ADD COLUMN visitante_id UUID REFERENCES visitante(id) ON DELETE SET NULL;

CREATE INDEX idx_inscricao_visitante ON inscricao_evento (visitante_id);
