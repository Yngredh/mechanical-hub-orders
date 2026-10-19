-- Ambiente local: no RDS a role e criada pelo mechanical-hub-database
-- (sql/auth-database-role.sql) antes das migrations do orders, que concedem
-- o SELECT em users/profiles a ela.
CREATE ROLE mechanical_hub_auth WITH LOGIN PASSWORD 'mechanical_hub_auth';
GRANT CONNECT ON DATABASE mechanical_hub_orders TO mechanical_hub_auth;
