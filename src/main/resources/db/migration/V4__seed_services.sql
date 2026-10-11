-- Catalogo de reparos: exatamente design/seed_catalog.md §1 (mesmos repairId
-- de stock e billing). Sem preco: o preco e do billing.
INSERT INTO services (id, name, description) VALUES
    ('03d94934-6201-449b-b8db-490546c35d34', 'Troca de Óleo e Filtro',
     'Substituição do óleo do motor e filtro de óleo conforme especificação do fabricante.'),
    ('b9090e29-c8f8-498f-b536-bb70cb69113e', 'Manutenção de Sistema de Freios',
     'Substituição de pastilhas de freio dianteiras e fluido de freio para garantir eficiência na frenagem.'),
    ('192c4db5-61b3-4cf7-b02b-54e948a9ce5d', 'Substituição de Correia Dentada',
     'Troca da correia dentada e tensores conforme intervalo recomendado pelo fabricante, prevenindo falhas no motor.'),
    ('eeff2222-2222-2222-2222-000000000001', 'Revisão do Sistema de Ignição',
     'Substituição de velas, cabos de vela e bobina de ignição para restaurar o desempenho do motor.');
