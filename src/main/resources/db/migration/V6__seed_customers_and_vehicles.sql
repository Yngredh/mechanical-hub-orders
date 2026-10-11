-- Clientes e veiculos do monolito (V16__seed_mechanical_hub.sql), mesmos UUIDs.
-- O e-mail do cliente e o destino das notificacoes.
INSERT INTO customers (id, name, document_type, document_number, telephone, email, address) VALUES
    ('96052f8d-0817-4124-84ad-918b7c9ac73d', 'Carlos Eduardo Mendes', 'CPF', '52998224725', '5511987453210',
     'carlos.mendes@gmail.com', 'Rua das Flores, 123, São Paulo, SP'),
    ('69c2954a-3606-48f4-a38b-28a50062e962', 'Transportadora Rota Livre Ltda', 'CNPJ', '11222333000181', '551133445566',
     'contato@rotalivretransportes.com.br', 'Avenida dos Transportes, 456, São Paulo, SP'),
    ('a6a6e27c-a061-4416-82dc-80233eda96d0', 'Salvio Silva', 'CPF', '12345678909', '551133445566',
     'salvio.silva@gmail.com', 'Av. Industrial, 890, Distrito Industrial, Guarulhos - SP, 07220-000'),
    ('eeff0000-b542-47ca-d28e-458796413054', 'Marcos Ferreira', 'CPF', '98765432100', '5511999887766',
     'marcos.ferreira@email.com', 'Rua dos Mecânicos, 789, Campinas, SP');

INSERT INTO vehicles (id, customer_id, license_plate, brand, model, year, color) VALUES
    ('d0803851-6a5f-4fea-8463-a723be0e390c', '96052f8d-0817-4124-84ad-918b7c9ac73d', 'BRA2E19', 'Volkswagen', 'Gol 1.0', 2021, 'Branco'),
    ('00000000-0000-0000-0000-000000000011', '69c2954a-3606-48f4-a38b-28a50062e962', 'ABC1234', 'Chevrolet', 'S10 2.8 Turbo Diesel', 2018, 'Prata'),
    ('241ad3cc-f878-49d3-9292-94ca1d4912d9', '69c2954a-3606-48f4-a38b-28a50062e962', 'EFG2142', 'Renault', 'Kwid', 2022, 'Branco'),
    ('aaff0000-b544-47cb-d28a-988796413047', 'eeff0000-b542-47ca-d28e-458796413054', 'PQR5H67', 'Fiat', 'Uno 1.0', 2019, 'Vermelho');
