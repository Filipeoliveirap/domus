ALTER TABLE usuario ADD COLUMN role_id UUID;

UPDATE usuario u
SET role_id = ur.role_id
    FROM usuario_role ur
WHERE ur.usuario_id = u.id;

ALTER TABLE usuario ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_role
    FOREIGN KEY (role_id) REFERENCES role(id);


DROP TABLE usuario_role;