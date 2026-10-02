-- Productos e inventario
INSERT INTO productos (id, precio) VALUES (1, 100000.0);
INSERT INTO productos (id, precio) VALUES (2, 500000.0);
INSERT INTO inventario (producto_id, stock) VALUES (1, 50);
INSERT INTO inventario (producto_id, stock) VALUES (2, 5);

-- Clientes
INSERT INTO clientes (id, tipo_cliente) VALUES (1, 'VIP');
INSERT INTO clientes (id, tipo_cliente) VALUES (2, 'FRECUENTE');
INSERT INTO clientes (id, tipo_cliente) VALUES (3, 'MOROSO');
INSERT INTO clientes (id, tipo_cliente) VALUES (4, 'ESTANDAR');

-- Factura pendiente para cliente moroso (ID 3)
INSERT INTO facturas (cliente_id, monto, pagada) VALUES (3, 150000.0, false);

-- Historial de pedidos previos para cliente FRECUENTE (ID 2) para activar descuento
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
SELECT 2, 100000, 0, 19000, 119000, CURRENT_TIMESTAMP, 'CONFIRMADO'
FROM SYSTEM_RANGE(1, 4);