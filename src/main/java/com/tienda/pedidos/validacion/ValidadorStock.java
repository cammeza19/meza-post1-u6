package com.tienda.pedidos.validacion;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ValidadorStock extends ValidadorPedido {
    private final JdbcTemplate jdbcTemplate;

    public ValidadorStock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        for (var item : contexto.getRequest().getItems()) {
            Integer stock;
            try {
                stock = jdbcTemplate.queryForObject(
                    "SELECT stock FROM inventario WHERE producto_id = ?",
                    Integer.class, item.getProductoId());
            } catch (EmptyResultDataAccessException e) {
                stock = 0;
            }

            if (stock == null || stock < item.getCantidad()) {
                contexto.rechazar("Stock insuficiente: producto " + item.getProductoId());
                return;
            }
        }
    }
}