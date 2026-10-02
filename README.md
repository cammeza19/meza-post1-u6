# Post-contenido Unidad 6: Antipatrones de Diseño
**Materia:** Patrones de Diseño de Software  
**Proyecto:** `pedidos-service`  

---

## 1. Diagnóstico de Antipatrones — Parte 1 (`GestorPedidos.java`)

Tras analizar la clase `GestorPedidos.java` y su método principal `procesarPedido`, se identificó la coexistencia de dos antipatrones principales: **God Object** y **Spaghetti Code**.

---

### A. Antipatrón: God Object (God Class / Blob)

#### 1. Evidencia concreta en el código
La clase `GestorPedidos` viola el **Principio de Responsabilidad Única (SRP)** al concentrar seis (6) responsabilidades completamente distintas dentro de un único método público (`procesarPedido`):

* **Validación de stock (Líneas 30–45):** Ejecuta consultas SQL directas a la tabla `inventario` para verificar las cantidades disponibles.
* **Validación de cliente y mora (Líneas 48–74):** Consulta datos de la tabla `clientes`, calcula la deuda en la tabla `facturas` y evalúa excepciones por horario (`LocalTime.now()`).
* **Cálculo de subtotal y precios (Líneas 77–83):** Realiza iteraciones sobre los ítems ejecutando una consulta SQL individual por producto para obtener sus precios.
* **Cálculo de reglas de descuento e impuestos (Líneas 86–107):** Aplica lógica de negocio para determinar porcentajes de descuento según tipo de cliente (`VIP`, `FRECUENTE`) e historial de pedidos, calculando IVA y totales.
* **Persistencia e inserción directa JDBC (Líneas 110–137):** Manipula transacciones de base de datos directamente, insertando registros en `pedidos` y `detalle_pedido`, y actualizando el stock en `inventario` usando `KeyHolder` / `PreparedStatement`.
* **Construcción de notificación y correo (Líneas 140–154):** Construye mediante `StringBuilder` el texto del mensaje de correo en formato plano y delega su envío a `EmailService`.

#### 2. Razones para cambiar (Múltiples ejes de cambio)
Esta clase cambiará por cualquiera de los siguientes motivos independientes:
1. Si cambia la estrategia para consultar el stock o la estructura de la base de datos.
2. Si cambian las políticas de mora de clientes o el horario límite de corte.
3. Si cambian los porcentajes o criterios para otorgar descuentos.
4. Si cambia la tecnología de persistencia (ej. pasar de JDBC plano a un Repositorio/ORM).
5. Si cambia la plantilla o el formato del correo de notificación.

#### 3. Múltiples niveles de abstracción
El método opera simultáneamente en niveles de abstracción incompatibles: manipulación de SQL a bajo nivel, reglas de negocio de alto nivel (descuentos y mora) y formateo de cadenas de texto plano (`StringBuilder`).

---

### B. Antipatrón: Spaghetti Code

#### 1. Evidencia concreta en el código
* **Niveles de anidamiento condicional:** El bloque de cálculo de descuento (líneas 86–105) presenta múltiples condicionales anidados (`if-else`) de hasta 3 niveles de profundidad para bifurcar la lógica según si el cliente es `VIP` o `FRECUENTE` y evaluar subtotales o conteo de pedidos previos.
* **Validación de mora compleja:** La validación de cliente (líneas 48–74) mezcla verificación de existencia, cálculo de deuda acumulada y evaluación de condicionales temporales (`LocalTime.of(20, 0)`).

#### 2. Dificultad de mantenimiento y extensión (Violación de OCP)
Si se requiere agregar un nuevo tipo de cliente (por ejemplo, `CORPORATIVO`) con reglas de descuento personalizadas, sería obligatorio modificar directamente el método `procesarPedido` agregando más bloques `else-if` en medio de la secuencia lógica. Esto viola el **Principio Abierto/Cerrado (OCP)** y aumenta exponencialmente el riesgo de introducir regresiones en la validación o persistencia de pedidos existentes.

## Decisiones de diseño (Parte 1)

### Antipatrones identificados
**God Object y Spaghetti Code combinados.**  
El método `procesarPedido()` en `GestorPedidos.java` mezclaba 6 responsabilidades distintas (validación de stock, validación de cliente y mora, cálculo de subtotal, cálculo de descuento con hasta 3 niveles de anidamiento condicional, persistencia vía JDBC directo y notificación por correo) dentro de un único método de más de 100 líneas, violando el Principio de Responsabilidad Única (SRP).

---

### Patrones aplicados y justificación

#### Decisión con justificación — validación como Chain of Responsibility
Se eligió **Chain of Responsibility** para la secuencia de validaciones y no una lista de métodos booleanos invocados en orden porque las validaciones tienen una dependencia real de orden y de corte anticipado: si `ValidadorStock` rechaza el pedido, `ValidadorCliente` ni siquiera debe ejecutarse. Una alternativa considerada fue un método `validarTodo()` con una lista de `Predicate<ContextoPedido>`, pero esa alternativa evalúa todos los predicados aunque el primero ya haya fallado, y no permite que un validador decida no delegar al siguiente — el corte anticipado que sí ofrece la cadena.

#### Decisión con justificación — descuento como Strategy y no como parte de la cadena
Se eligió **Strategy** y no un eslabón más de la cadena de validación para el cálculo de descuento porque, a diferencia de las validaciones, las reglas de descuento no tienen una dependencia de orden entre sí ni necesitan la posibilidad de "cortar" el flujo: siempre se aplica exactamente una regla, determinada por el tipo de cliente. Modelarlo como cadena habría obligado a introducir un mecanismo artificial para garantizar que solo un eslabón module el descuento, cuando un mapa de selección directa (Strategy + Factory simple con `SelectorEstrategiaDescuento`) resuelve el problema con menos indirección y sin condicionales.

#### Extracción de responsabilidades de infraestructura (SRP)
Se extrajo la persistencia directa a la clase `PedidoRepository` (@Repository) y la notificación por correo a `NotificacionPedidoService` (@Service). Con esto, `GestorPedidos` se reduce a un orquestador delgado que únicamente coordina el flujo entre las capas sin conocer sus detalles de implementación internos.