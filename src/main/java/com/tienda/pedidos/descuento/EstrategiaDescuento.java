package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;

public interface EstrategiaDescuento {
    double calcular(ContextoPedido contexto);
}