# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción
Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software (Sexto Semestre). Un único proyecto Spring Boot (`pedidos-service/`) que aborda en dos partes el diagnóstico y refactorización de antipatrones de diseño:
1. **Parte 1:** Diagnóstico y refactorización de un antipatrón combinado (**God Object** y **Spaghetti Code**) en la clase `GestorPedidos`.
2. **Parte 2:** Diagnóstico y corrección de un segundo antipatrón (**Golden Hammer**) introducido durante un ciclo de crecimiento al agregar tres campañas de descuento promocional.
---

## Comparación y Métricas de Refactorización (Antes vs. Después)

### 1. Métricas Cuantitativas del Código
| Métrica | Estado Inicial (Monolítico) | Estado Refactorizado Final | Impacto de la Mejora |
| :--- | :--- | :--- | :--- |
| **Líneas en `GestorPedidos.java`** | 128 líneas (340 líneas en clase base) | 58 líneas | **Reducción del 54.6%** en complejidad |
| **Nivel de Anidamiento Máximo** | 3 niveles (`if MOROSO` -> `if deuda > 0` -> `if fueraDeHorario`) | 1 nivel (Estructura plana / Guard Clauses) | **Mayor legibilidad y menor complejidad ciclomática** |
| **Responsabilidades en `GestorPedidos`** | 6 responsabilidades mezcladas | 1 responsabilidad (Orquestador delgado) | **Cumplimiento estricto de SRP** |
| **Clases / Componentes Cohesivos** | 1 única clase gigante | 11 componentes especializados | **Alta cohesión y bajo acoplamiento** |
| **Cobertura y Pruebas Unitarias** | Acoplado e inestable | 8/8 Pruebas Unitarias Pasando (`BUILD SUCCESS`) | **100% Testeable y Mantenible** |

---

### 2. Evidencia de la Salida del Sistema (Consola)

#### **Antes (Procesamiento Monolítico)**
```text
[LOG] Iniciando procesamiento de pedido para cliente ID: 3
[SQL] SELECT stock FROM inventario WHERE producto_id = 1
[SQL] SELECT tipo_cliente FROM clientes WHERE id = 3
[SQL] SELECT SUM(monto) FROM facturas WHERE cliente_id = 3 AND pagada = false
[LOG] Pedido rechazado: Cliente con deuda pendiente: $150000.0
[WARN] Fallo en procesamiento en linea 42 de GestorPedidos.java
```
---

## Decisiones de Diseño y Diagnóstico de Antipatrones

### Parte 1 — Refactorización de GestorPedidos (God Object / Spaghetti Code)

#### **Diagnóstico del Antipatrón**

La clase `GestorPedidos` actuaba como un **God Object** al asumir 6 responsabilidades totalmente distintas dentro de un único método:

1. Validación de disponibilidad de stock en base de datos.
2. Validación del estado del cliente y cálculo de mora en horarios de corte.
3. Consulta y cálculo del subtotal de productos.
4. Evaluación condicional de reglas de descuento según tipo de cliente.
5. Persistencia directa en base de datos (`pedidos`, `detalle_pedido` e `inventario`) vía JDBC.
6. Construcción y envío de notificaciones por correo electrónico.

Adicionalmente, presentaba **Spaghetti Code** con un anidamiento profundo en la validación de mora (`if (tipo.equals("MOROSO"))` -> `if (deuda > 0)` -> `if (!fueraDeHorario)`), mezclando lógica de negocio con sentencias SQL embebidas.

#### **Patrones Aplicados**

- **Chain of Responsibility (`ValidadorStock` -> `ValidadorCliente`):** Se eligió para las validaciones debido a la necesidad real de **orden estricto y corte anticipado**. Si no hay stock disponible, el pedido se rechaza inmediatamente sin ejecutar consultas de mora a la base de datos.

- **Strategy (`EstrategiaDescuento`):** Implementado con `DescuentoVip`, `DescuentoFrecuente` y `DescuentoEstandar`. A diferencia de las validaciones, los descuentos no requieren interrupción del flujo; solo se evalúa la regla correspondiente mediante `SelectorEstrategiaDescuento`.

- **Separación de Capas (SRP):** Extracción de la persistencia SQL a `PedidoRepository` y la lógica de correo a `NotificacionPedidoService`.

#### **Alternativa Descartada**

Se descartó usar un método `validarTodo()` basado en una lista de `Predicate<ContextoPedido>`. Dicha alternativa habría forzado la evaluación inútil de todos los predicados sobre el pedido, perdiendo la capacidad de corte anticipado directo que brinda la cadena.

### Parte 2 — Crecimiento del Sistema (Golden Hammer)

#### **Diagnóstico del Antipatrón**

Al incorporar las promociones `BLACK_FRIDAY`, `CORPORATIVO` y `VOLUMEN`, se cometió el error de heredarlas de `ValidadorPedido` e insertarlas como eslabones en la cadena existente (**Golden Hammer**).

- **Violación de Abstracción:** Las promociones jamás rechazan un pedido (`contexto.rechazar(...)`), por lo que no eran validadores reales. Solo abusaban de la estructura para mutar la variable `descuentoCampana`.

- **Rigor estructural vs. Flexibilidad:** La cadena imponía un orden artificial. Si el negocio requiriera sumar dos promociones en lugar de aplicar la regla de "el mayor gana" (ej. acumular `VOLUMEN` + `CORPORATIVO`), la cadena habría fallado totalmente al depender de mutaciones secuenciales rígidas.

#### **Solución Aplicada**

Se eliminaron por completo las clases `PromocionBlackFriday`, `PromocionCorporativo` y `PromocionVolumen` de la cadena, transformándolas en estrategias (`DescuentoBlackFriday`, `DescuentoCorporativo`, `DescuentoVolumen`) gestionadas por `CalculadorDescuentoFinal`.

#### **Prevención de Lava Flow**

No se dejó ningún bloque de código obsoleto ni clases comentadas "por si acaso". Las clases mal aplicadas y la propiedad `descuentoCampana` se eliminaron definitivamente mediante comandos de Git (`git rm`), garantizando un código limpio y confiable en el presente.

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

- **Impacto Cuantitativo:** La refactorización permitió reducir en un **54.6% las líneas de código** de la clase principal, aplanar el anidamiento de 3 niveles a 1, y distribuir 6 responsabilidades en 11 componentes con propósitos claramente delimitados.

- **Mantenibilidad Cualitativa:** La aplicación de *Chain of Responsibility* y *Strategy* convirtió un monolito frágil en una arquitectura extensible. Agregar una nueva regla de validación o un nuevo descuento no requiere modificar `GestorPedidos`.

- **Lección sobre Antipatrones:** Reutilizar un patrón exitoso en un problema con requerimientos distintos genera un *Golden Hammer*. Asimismo, mantener el repositorio libre de código muerto previene la degradación por *Lava Flow*, confiando la memoria histórica a las herramientas de control de versiones como Git.