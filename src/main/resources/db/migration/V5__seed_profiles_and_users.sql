-- Perfis e funcionarios do monolito (V3__seed_profiles.sql,
-- V15__seed_default_users.sql e V19__backfill_users_document_number.sql).
-- No monolito esses ids eram gerados na migration (uuid_generate_v4); aqui
-- ficam fixos para que todos os ambientes tenham os mesmos ids.
-- profiles.name segue o enum Role da Lambda (ADMINISTRATOR, MECHANICAL).
INSERT INTO profiles (id, name, description) VALUES
    ('5d1f3c2a-7b8e-4a90-9c1d-000000000001', 'ADMINISTRATOR', 'Administrador'),
    ('5d1f3c2a-7b8e-4a90-9c1d-000000000002', 'MECHANICAL', 'Mecânico');

-- Senhas (bcrypt) iguais as do monolito. Somente para demonstracao.
INSERT INTO users (id, profile_id, name, email, document_number, password_hash) VALUES
    ('7a2e9b41-3c5d-4f60-8e1a-000000000001', '5d1f3c2a-7b8e-4a90-9c1d-000000000001',
     'Administrador Padrão', 'admin@mechanicalhub.com', '52998224725',
     '$2a$10$X9/XYPVGcZt6C0zRm9ncSumgLtJ2fb1QDHFcZnh0xVfNfuOEo7HI6'),
    ('7a2e9b41-3c5d-4f60-8e1a-000000000002', '5d1f3c2a-7b8e-4a90-9c1d-000000000002',
     'Mecânico Padrão', 'mecanico@mechanicalhub.com', '11144477735',
     '$2a$10$sDiafR.rr1XPTXd5GR1bKehHse75Hw6p.Ha9W2Mp5kv0GScc0LECe');
