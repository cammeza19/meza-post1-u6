package com.tienda.pedidos.validacion;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class ValidadorCliente extends ValidadorPedido {
    private final JdbcTemplate jdbcTemplate;

    public ValidadorCliente(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        Long clienteId = contexto.getRequest().getClienteId();
        String tipo;
        try {
            tipo = jdbcTemplate.queryForObject(
                "SELECT tipo_cliente FROM clientes WHERE id = ?", String.class, clienteId);
        } catch (EmptyResultDataAccessException e) {
            tipo = null;
        }

        if (tipo == null) {
            contexto.rechazar("Cliente no registrado");
            return;
        }

        contexto.setTipoCliente(tipo);

        if (tipo.equals("MOROSO")) {
            Double deuda;
            try {
                deuda = jdbcTemplate.queryForObject(
                    "SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false",
                    Double.class, clienteId);
            } catch (EmptyResultDataAccessException e) {
                deuda = 0.0;
            }

            boolean fueraDeHorarioDeCorte = !LocalTime.now().isBefore(LocalTime.of(20, 0));
            if (deuda != null && deuda > 0 && !fueraDeHorarioDeCorte) {
                contexto.rechazar("Cliente con deuda pendiente: $" + deuda);
            }
        }
    }
}