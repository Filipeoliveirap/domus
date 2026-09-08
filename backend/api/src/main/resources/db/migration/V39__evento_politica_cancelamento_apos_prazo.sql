ALTER TABLE evento
    ADD COLUMN politica_cancelamento_apos_prazo VARCHAR(30)
        NOT NULL DEFAULT 'PERMITIDO_COM_REEMBOLSO';

-- Backfill do boolean V38: true = "permitia cancelar" = com reembolso; false = não permitia.
UPDATE evento SET politica_cancelamento_apos_prazo =
    CASE WHEN permite_cancelar_apos_prazo THEN 'PERMITIDO_COM_REEMBOLSO'
         ELSE 'NAO_PERMITIDO' END;

ALTER TABLE evento DROP COLUMN permite_cancelar_apos_prazo;
