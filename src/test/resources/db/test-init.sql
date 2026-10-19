-- No RDS a role vem do mechanical-hub-database; nos testes e criada aqui,
-- antes das migrations, que concedem o SELECT a ela.
CREATE ROLE mechanical_hub_auth NOLOGIN;
