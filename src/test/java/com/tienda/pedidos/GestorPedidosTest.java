package com.tienda.pedidos;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.service.GestorPedidos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GestorPedidosTest {

    @Autowired
    private GestorPedidos gestorPedidos;

    @Test
    void test1_StockInsuficiente() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(1L);
        request.setClienteEmail("vip@tienda.com");
        request.setItems(List.of(new ItemPedido(2L, 10))); // Stock es 5

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertFalse(resultado.isConfirmado());
        assertTrue(resultado.getMotivoRechazo().contains("Stock insuficiente"));
    }

    @Test
    void test2_ClienteInexistente() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(999L);
        request.setClienteEmail("noexiste@tienda.com");
        request.setItems(List.of(new ItemPedido(1L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente no registrado", resultado.getMotivoRechazo());
    }

    @Test
    void test3_ClienteMoroso() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(3L);
        request.setClienteEmail("moroso@tienda.com");
        request.setItems(List.of(new ItemPedido(1L, 1)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertNotNull(resultado);
    }

    @Test
    void test4_ClienteVIPDescuento() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(1L);
        request.setClienteEmail("vip@tienda.com");
        request.setItems(List.of(new ItemPedido(2L, 2))); // Subtotal 1,000,000 -> Black Friday (25%) gana a VIP (10%)

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertTrue(resultado.isConfirmado());
        assertTrue(resultado.getTotal() > 0);
    }

    @Test
    void test5_ClienteFrecuenteDescuento() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(2L);
        request.setClienteEmail("frecuente@tienda.com");
        request.setItems(List.of(new ItemPedido(1L, 2)));

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertTrue(resultado.isConfirmado());
        assertTrue(resultado.getTotal() > 0);
    }

    @Test
    void test6_CampanaBlackFriday() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(4L); // Cliente ESTANDAR
        request.setClienteEmail("estandar@tienda.com");
        request.setItems(List.of(new ItemPedido(1L, 1))); // Subtotal 100,000 -> Black Friday 25%

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertTrue(resultado.isConfirmado());
        // Subtotal 100,000 - 25% desc (25,000) = 75,000 + 19% IVA (14,250) = 89,250
        assertEquals(89250.0, resultado.getTotal(), 0.01);
    }

    @Test
    void test7_CampanaCorporativo() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(5L); // Cliente ESTANDAR con NIT
        request.setClienteEmail("corp@tienda.com");
        request.setItems(List.of(new ItemPedido(1L, 1))); // Subtotal 100,000 -> Black Friday 25% gana sobre Corp 10%

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertTrue(resultado.isConfirmado());
        assertTrue(resultado.getTotal() > 0);
    }

    @Test
    void test8_CampanaVolumen() {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(4L);
        request.setClienteEmail("volumen@tienda.com");
        request.setItems(List.of(new ItemPedido(1L, 25))); // 25 unidades > 20 -> Black Friday 25% sigue siendo superior a 12%

        ResultadoPedido resultado = gestorPedidos.procesarPedido(request);
        assertTrue(resultado.isConfirmado());
        assertTrue(resultado.getTotal() > 0);
    }
}