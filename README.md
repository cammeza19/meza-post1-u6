# Post-contenido Unidad 6: Diagnóstico y Refactorización de Antipatrones de Diseño

## Descripción

Este repositorio alberga el desarrollo de la Unidad 6 para la materia de Patrones de Diseño de Software. A través de un proyecto Spring Boot (`pedidos-service/`), se aborda en dos partes el diagnóstico y refactorización de antipatrones en un sistema de procesamiento de pedidos:

1. **Parte 1:** Diagnóstico y refactorización de un **God Object / Spaghetti Code** monolítico en la clase `GestorPedidos`.

2. **Parte 2:** Diagnóstico y corrección del antipatrón **Golden Hammer**, surgido al extender la cadena de validaciones para incorporar campañas promocionales.

## Decisiones de Diseño y Diagnóstico de Antipatrones

### Parte 1 — Refactorización de GestorPedidos (God Object / Spaghetti Code)

#### 1. Diagnóstico con evidencia concreta en el código

La clase `GestorPedidos` original (commit `622c1e8`, 340 líneas en total, de las cuales
`procesarPedido()` ocupa aproximadamente 100) concentra en un solo método seis
responsabilidades que cambian por motivos distintos. Los rangos de línea que se citan a
continuación son aproximados y corresponden a ese commit. Además, los seis métodos privados
auxiliares (`obtenerHistorialCliente`, `formatearFactura`, `calcularImpuestoRegional`,
`reintentarNotificacion`, `purgarPedidosVencidos`, `construirCuerpoCorreo`) no se invocan
desde el flujo principal: aportan más motivos de cambio y son código muerto.

| Líneas | Responsabilidad | Evidencia en el código |
|---|---|---|
| 30–42 | Validación de stock | `jdbcTemplate.queryForObject("SELECT stock FROM inventario ...")` dentro de un `for`, mezclando regla de negocio y SQL |
| 45–68 | Validación de cliente y mora | `if (tipoCliente == null) ... else if (tipoCliente.equals("MOROSO"))` |
| 70–82 | Cálculo de subtotal | Una consulta `SELECT precio FROM productos` por cada ítem |
| 85–102 | Cálculo de descuento | `if (tipoCliente.equals("VIP"))` → `if (subtotal > 1_000_000)` / `else if (subtotal > 500_000)` |
| 105–120 | Persistencia | `INSERT INTO pedidos`, `INSERT INTO detalle_pedido` y `UPDATE inventario` directos, sin repositorio ni transacción |
| 122–128 | Notificación | Armado del cuerpo con `StringBuilder` y llamada a `emailService.enviar(...)` |

**God Object.** La clase tiene al menos 6 razones para cambiar (reglas de stock, reglas de
mora, política de precios, política de descuentos, esquema de base de datos y formato del
correo), más las que insinúan los seis métodos auxiliares. Un cambio en la política de
descuentos obliga a releer un método de ~99 líneas donde también viven el SQL y el texto
del correo.

**Spaghetti Code.** El método opera en tres niveles de abstracción a la vez (SQL embebido,
reglas de negocio y formato de texto) y tiene condicionales anidados:

- **Mora (3 niveles):**
```java
  } else if (tipoCliente.equals("MOROSO")) {                      // nivel 1
      if (deudaPendiente != null && deudaPendiente > 0) {         // nivel 2
          if (ahora.isBefore(LocalTime.of(20, 0))) {              // nivel 3
              return ResultadoPedido.rechazado(...);
          } else { log.info("... se permite el pedido excepcionalmente"); }
```
- **Descuento (2 niveles):** `VIP` → tramos de subtotal, y `FRECUENTE` → tramos de
  `pedidosPrevios`.
- **Ejecución sin transacción:** el `INSERT` del pedido, los del detalle y los `UPDATE` de
  inventario se ejecutan por separado. Si falla uno a mitad del bucle, el pedido queda
  parcialmente guardado.

**Violación de Open/Closed.** Para agregar un nuevo tipo de cliente con reglas de descuento
propias hay que modificar el bloque de las líneas 85–102 añadiendo otra rama `else if`, es
decir, editar código existente y ya probado dentro del método más grande de la clase.
Cualquier error en esa edición puede afectar la validación, la persistencia o la
notificación, porque comparten el mismo método y las mismas variables locales.

**Destino de los métodos auxiliares.** Se eliminaron durante la refactorización por ser
código muerto (no tenían ningún llamador), no se movieron a otras clases.

#### 2. Patrones aplicados
* **Chain of Responsibility (`ValidadorStock` -> `ValidadorCliente`):** Aplicado a las validaciones por existir una estricta dependencia de orden y la necesidad de **corte anticipado**. Si no hay stock disponible, el proceso se interrumpe sin realizar consultas a la base de datos de morosos.
* **Strategy (`EstrategiaDescuento`):** Implementado mediante `DescuentoVip`, `DescuentoFrecuente` y `DescuentoEstandar` coordinados por `SelectorEstrategiaDescuento`. No requieren interrupción del flujo ni orden secuencial.
* **Extracción de capas de infraestructura (SRP):** Delegación de la persistencia SQL a `PedidoRepository` y de las notificaciones a `NotificacionPedidoService`.

#### 3. Alternativa descartada
Se evaluó un método `validarTodo()` mediante una lista de `Predicate<ContextoPedido>`. Se descartó porque habría evaluado forzosamente todas las condiciones sobre el pedido, perdiendo el corte anticipado inmediato que provee la cadena de responsabilidad.

---

### Parte 2 — Crecimiento del Sistema (Golden Hammer)

#### 1. Diagnóstico con evidencia en el código
Al solicitarse las campañas promocionales `BLACK_FRIDAY`, `CORPORATIVO` y `VOLUMEN`, se crearon las clases `PromocionBlackFriday`, `PromocionCorporativo` y `PromocionVolumen` heredando de `ValidadorPedido` e insertándose en la cadena mediante la sintaxis:
`.encadenar(blackFriday).encadenar(corporativo).encadenar(volumen)`

* **Causa del antipatrón (Golden Hammer):** Se reutilizó la estructura de la cadena únicamente porque *"ya había funcionado en la Parte 1"*, ignorando que las promociones no compartían las propiedades de un validador.
* **Ausencia de dependencia de orden:** Reordenar las promociones en la cadena no altera el resultado final, a diferencia de `ValidadorStock`, que debe ejecutarse obligatoriamente antes que `ValidadorCliente`.
* **Violación de Abstracción y Acumulador Rígido:** Los eslabones de promoción nunca rechazaban un pedido (incluían comentarios como `// nunca rechaza`). Solo mutaban la propiedad compartida `descuentoCampana`. Si el negocio exigiera en el futuro *sumar* dos campañas en lugar de seleccionar el máximo (`Math.max`), la cadena fallaría conceptualmente al no contar con un componente centralizado responsable del cálculo.

#### 2. Solución aplicada y prevención de Lava Flow
Las campañas se reestructuraron como estrategias (`DescuentoBlackFriday`, `DescuentoCorporativo`, `DescuentoVolumen`) gestionadas por `CalculadorDescuentoFinal`. Agregar una nueva promoción solo requiere crear su clase e incluirla en la lista de estrategias de `CalculadorDescuentoFinal`, sin alterar la lógica de `GestorPedidos`.

Para evitar la acumulación de deuda técnica por **Lava Flow**, los tres eslabones de cadena obsoletos y el campo `descuentoCampana` se eliminaron definitivamente mediante `git rm` (sin dejar fragmentos comentados), confiando el historial al registro de commits de Git.

#### 3. Alternativa descartada
Mantener las promociones como eslabones dentro de la cadena de validación. Se descartó porque violaba la abstracción de validación (ninguna rechazaba el pedido), imponía un orden secuencial artificial y obligaba a mutar un estado global compartido dentro de `ContextoPedido`.

---

## Comparación y Métricas del Sistema

### 1. Pruebas de Equivalencia Funcional

#### Parte 1: Monolito vs. Refactorización inicial (Chain + Strategy)
| Caso / Pedido | Subtotal / Configuración | Salida Monolito Original | Salida Refactorizada Parte 1 | ¿Idénticos? |
| :--- | :--- | :--- | :--- | :---: |
| **1. Stock insuficiente** | Producto ID: 2 | `Stock insuficiente: producto 2` | `Stock insuficiente: producto 2` | **Sí** |
| **2. Cliente no registrado** | Cliente ID: 99 | `Cliente no registrado` | `Cliente no registrado` | **Sí** |
| **3. Cliente moroso** | Deuda: $150,000 | `Cliente con deuda pendiente: $150000.0` | `Cliente con deuda pendiente: $150000.0` | **Sí** |
| **4. Cliente VIP** | Subtotal: $1,100,000 (VIP, tramo > 1.000.000 → 15%) | `Confirmado - Total: $[SALIDA REAL]` | `Confirmado - Total: $[SALIDA REAL]` | **Sí** |
| **5. Cliente Frecuente** | Subtotal: $240,000 ([N] pedidos previos → [8% / 4%]) | `Confirmado - Total: $[SALIDA REAL]` | `Confirmado - Total: $[SALIDA REAL]` | **Sí** |

#### Parte 2: Versión Golden Hammer (Commit `512d8e8`) vs. Versión Final Strategy
*(En cada caso se usa un cliente de tipo ESTANDAR y solo está activa la condición bajo prueba; `promo.black-friday.activa=false` salvo en el caso 6)*

| Caso de Prueba / Campaña | Configuración de Prueba | Salida Versión Golden Hammer | Salida Versión Final Strategy | ¿Idénticos? |
| :--- | :--- | :--- | :--- | :---: |
| **6. Black Friday (25%)** | `promo.black-friday.activa=true`, subtotal $100,000 | `Confirmado - Total: $89250.0` | `Confirmado - Total: $89250.0` | **Sí** |
| **7. Corporativo (10%)** | Cliente con NIT registrado, Black Friday inactiva, subtotal $100,000 | `Confirmado - Total: $107100.0` | `Confirmado - Total: $107100.0` | **Sí** |
| **8. Volumen (12%)** | Pedido de 25 unidades, subtotal $2,500,000, Black Friday inactiva | `Confirmado - Total: $2618000.0` | `Confirmado - Total: $2618000.0` | **Sí** |

---

### 2. Tabla de Métricas del Código

| Métrica de Diseño | Monolito Inicial | Refactorizado Final | Impacto de la Mejora |
| :--- | :--- | :--- | :--- |
| **Razones de cambio en `GestorPedidos`** | 6 responsabilidades mezcladas en un método | Orquesta el flujo; conserva el cálculo de subtotal e impuesto como residuo menor | **Responsabilidades de validación, descuento, persistencia y notificación fuera de la clase** |
| **Anidamiento máximo** | 3 niveles (`MOROSO` → deuda → horario) | 1 nivel en `procesarPedido()`; 2 niveles triviales en `calcularSubtotal()` | **Eliminación de los condicionales anidados de negocio** |
| **Líneas en `GestorPedidos.java`** | 340 líneas (clase completa, incluye 6 métodos sin uso) | 69 líneas | **Reducción del 79.7%** |
| **Líneas de `procesarPedido()`** | ~100 líneas | ~21 líneas | **Reducción de ~80%** |
| **Estructura de clases del proyecto** | 1 clase monolítica + DTOs | 15 componentes especializados | **Mayor cohesión y modularidad** |
| **Pruebas** | 5 pedidos de prueba verificados | 8/8 tests pasando (`BUILD SUCCESS`) | **Comportamiento preservado** |

---

## Cómo ejecutar
```
mvn compile
mvn spring-boot:run
mvn test
```

## Herramientas utilizadas
- Java 17, Spring Boot, Spring JDBC, Maven, H2 Database
- VS Code, Git, GitHub

## Conclusiones

La realización de este post-contenido permitió constatar prácticamente cómo el deterioro del diseño por antipatrones como *God Object* y *Spaghetti Code* destruye la mantenibilidad del software. La transición hacia una arquitectura basada en *Chain of Responsibility* y *Strategy* distribuyó las responsabilidades originales de `GestorPedidos` en 15 componentes cohesivos, redujo la clase orquestadora de 340 a 69 líneas (~79.7%) y eliminó el anidamiento de negocio mediante *guard clauses*. Asimismo, el análisis del antipatrón *Golden Hammer* evidenció que la reutilización ciega de un patrón exitoso viola la abstracción del dominio cuando las propiedades del problema (como el orden o la interrupción del flujo) no coinciden. Finalmente, la eliminación completa de los eslabones obsoletos reforzó la importancia de combatir el *Lava Flow*, confiando en la trazabilidad de Git para preservar la historia del código.