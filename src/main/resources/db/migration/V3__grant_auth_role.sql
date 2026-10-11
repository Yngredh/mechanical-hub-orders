-- A Lambda `authenticate` (mechanical-hub-auth) le users/profiles direto neste
-- banco com a role somente leitura mechanical_hub_auth, criada antes pelo
-- mechanical-hub-database (sql/auth-database-role.sql). Falha se a role nao
-- existir: e a ordem de provisionamento esperada.
GRANT USAGE ON SCHEMA public TO mechanical_hub_auth;

GRANT SELECT (id, name, document_number, password_hash, deleted_at, profile_id) ON users TO mechanical_hub_auth;
GRANT SELECT (id, name) ON profiles TO mechanical_hub_auth;
