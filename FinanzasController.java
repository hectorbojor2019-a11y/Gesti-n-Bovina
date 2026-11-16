package com.gestionbovina.gestionbovina.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finanzas")
@CrossOrigin(origins = "*")
public class FinanzasController {

    private final JdbcTemplate jdbcTemplate;

    public FinanzasController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /* =========================================================
       ESTADO / RESUMEN
       ========================================================= */

    @GetMapping("/estado")
    public Map<String, Object> estado() {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        Integer operaciones = jdbcTemplate.queryForObject(
                """
                SELECT
                    (SELECT COUNT(*) FROM dbo.compras) +
                    (SELECT COUNT(*) FROM dbo.ventas)
                """,
                Integer.class
        );

        Integer movimientos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.vw_movimientos_financieros",
                Integer.class
        );

        respuesta.put("ok", true);
        respuesta.put("modulo", "FINANZAS");
        respuesta.put("operaciones_registradas", operaciones == null ? 0 : operaciones);
        respuesta.put("movimientos_financieros", movimientos == null ? 0 : movimientos);

        return respuesta;
    }


    @GetMapping("/resumen")
    public Map<String, Object> resumen() {

        String sql = """
                SELECT
                    CAST(COALESCE(SUM(
                        CASE
                            WHEN naturaleza = N'INGRESO'
                             AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                            THEN monto ELSE 0
                        END
                    ),0) AS DECIMAL(18,2)) AS ingresos,

                    CAST(COALESCE(SUM(
                        CASE
                            WHEN naturaleza = N'EGRESO'
                             AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                            THEN monto ELSE 0
                        END
                    ),0) AS DECIMAL(18,2)) AS egresos,

                    CAST(
                        COALESCE(SUM(
                            CASE
                                WHEN naturaleza = N'INGRESO'
                                 AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                                THEN monto ELSE 0
                            END
                        ),0)
                        -
                        COALESCE(SUM(
                            CASE
                                WHEN naturaleza = N'EGRESO'
                                 AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                                THEN monto ELSE 0
                            END
                        ),0)
                    AS DECIMAL(18,2)) AS balance,

                    COUNT(CASE
                        WHEN UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                        THEN 1
                    END) AS movimientos_activos
                FROM dbo.vw_movimientos_financieros
                """;

        return jdbcTemplate.queryForMap(sql);
    }


    @GetMapping("/movimientos")
    public List<Map<String, Object>> movimientos() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_pago,
                    fecha,
                    naturaleza,
                    tipo_pago,
                    metodo_pago,
                    monto,
                    codigo_moneda,
                    referencia,
                    estado,
                    id_empleado,
                    observaciones
                FROM dbo.vw_movimientos_financieros
                ORDER BY fecha DESC, id_pago DESC
                """);
    }


    /* =========================================================
       LISTADO COMERCIAL - SOLO PRODUCTOS/INSUMOS
       ========================================================= */

    @GetMapping("/operaciones")
    public List<Map<String, Object>> operaciones() {

        return jdbcTemplate.queryForList("""
                SELECT
                    N'VENTA_PRODUCTO' AS tipo_operacion,
                    v.id_venta AS id_operacion,
                    v.fecha_venta AS fecha,
                    v.numero_factura AS documento,
                    c.nombre AS tercero,
                    emp.nombre_completo AS empleado,
                    v.total,
                    CASE
                        WHEN v.estado = N'Anulada' THEN N'Anulada'
                        WHEN COALESCE(d.total_devuelto,0) >= v.total AND v.total > 0
                            THEN N'Devuelta'
                        WHEN COALESCE(d.total_devuelto,0) > 0
                            THEN N'Devuelta parcial'
                        ELSE v.estado
                    END AS estado_operacion,
                    COALESCE(d.total_devuelto,0) AS total_devuelto,
                    v.observaciones
                FROM dbo.ventas v
                INNER JOIN dbo.clientes c
                    ON c.id_cliente = v.id_cliente
                LEFT JOIN dbo.vw_personal_control emp
                    ON emp.id_empleado = v.id_empleado
                OUTER APPLY (
                    SELECT SUM(x.monto) AS total_devuelto
                    FROM dbo.devoluciones x
                    WHERE x.id_venta = v.id_venta
                      AND x.estado = N'APLICADA'
                ) d

                UNION ALL

                SELECT
                    N'COMPRA_INSUMO',
                    co.id_compra,
                    co.fecha_compra,
                    co.numero_factura,
                    p.nombre,
                    emp.nombre_completo,
                    co.total,
                    CASE
                        WHEN co.estado = N'Cancelado' THEN N'Anulada'
                        WHEN COALESCE(d.total_devuelto,0) >= co.total AND co.total > 0
                            THEN N'Devuelta'
                        WHEN COALESCE(d.total_devuelto,0) > 0
                            THEN N'Devuelta parcial'
                        ELSE co.estado
                    END,
                    COALESCE(d.total_devuelto,0),
                    co.observaciones
                FROM dbo.compras co
                INNER JOIN dbo.proveedores p
                    ON p.id_proveedor = co.id_proveedor
                LEFT JOIN dbo.vw_personal_control emp
                    ON emp.id_empleado = co.id_empleado
                OUTER APPLY (
                    SELECT SUM(x.monto) AS total_devuelto
                    FROM dbo.devoluciones x
                    WHERE x.id_compra = co.id_compra
                      AND x.estado = N'APLICADA'
                ) d

                ORDER BY fecha DESC, id_operacion DESC
                """);
    }


    @GetMapping("/compras")
    public List<Map<String, Object>> compras() {

        return jdbcTemplate.queryForList("""
                SELECT
                    N'COMPRA_INSUMO' AS tipo_operacion,
                    co.id_compra AS id_operacion,
                    co.fecha_compra AS fecha,
                    co.numero_factura AS documento,
                    p.nombre AS tercero,
                    emp.nombre_completo AS empleado,
                    co.total,
                    CASE
                        WHEN co.estado = N'Cancelado' THEN N'Anulada'
                        WHEN COALESCE(d.total_devuelto,0) >= co.total AND co.total > 0
                            THEN N'Devuelta'
                        WHEN COALESCE(d.total_devuelto,0) > 0
                            THEN N'Devuelta parcial'
                        ELSE co.estado
                    END AS estado_operacion,
                    COALESCE(d.total_devuelto,0) AS total_devuelto,
                    co.observaciones
                FROM dbo.compras co
                INNER JOIN dbo.proveedores p
                    ON p.id_proveedor = co.id_proveedor
                LEFT JOIN dbo.vw_personal_control emp
                    ON emp.id_empleado = co.id_empleado
                OUTER APPLY (
                    SELECT SUM(x.monto) AS total_devuelto
                    FROM dbo.devoluciones x
                    WHERE x.id_compra = co.id_compra
                      AND x.estado = N'APLICADA'
                ) d
                ORDER BY co.fecha_compra DESC, co.id_compra DESC
                """);
    }


    @GetMapping("/ventas")
    public List<Map<String, Object>> ventas() {

        return jdbcTemplate.queryForList("""
                SELECT
                    N'VENTA_PRODUCTO' AS tipo_operacion,
                    v.id_venta AS id_operacion,
                    v.fecha_venta AS fecha,
                    v.numero_factura AS documento,
                    c.nombre AS tercero,
                    emp.nombre_completo AS empleado,
                    v.total,
                    CASE
                        WHEN v.estado = N'Anulada' THEN N'Anulada'
                        WHEN COALESCE(d.total_devuelto,0) >= v.total AND v.total > 0
                            THEN N'Devuelta'
                        WHEN COALESCE(d.total_devuelto,0) > 0
                            THEN N'Devuelta parcial'
                        ELSE v.estado
                    END AS estado_operacion,
                    COALESCE(d.total_devuelto,0) AS total_devuelto,
                    v.observaciones
                FROM dbo.ventas v
                INNER JOIN dbo.clientes c
                    ON c.id_cliente = v.id_cliente
                LEFT JOIN dbo.vw_personal_control emp
                    ON emp.id_empleado = v.id_empleado
                OUTER APPLY (
                    SELECT SUM(x.monto) AS total_devuelto
                    FROM dbo.devoluciones x
                    WHERE x.id_venta = v.id_venta
                      AND x.estado = N'APLICADA'
                ) d
                ORDER BY v.fecha_venta DESC, v.id_venta DESC
                """);
    }


    /* =========================================================
       CATÁLOGOS PARA FORMULARIOS
       ========================================================= */

    @GetMapping("/clientes")
    public List<Map<String, Object>> clientes() {
        return jdbcTemplate.queryForList("""
                SELECT
                    id_cliente,
                    nombre,
                    ruc,
                    cedula,
                    telefono,
                    email
                FROM dbo.clientes
                WHERE activo = 1
                ORDER BY nombre
                """);
    }


    @GetMapping("/proveedores")
    public List<Map<String, Object>> proveedores() {
        return jdbcTemplate.queryForList("""
                SELECT
                    id_proveedor,
                    nombre,
                    ruc,
                    telefono,
                    email
                FROM dbo.proveedores
                WHERE activo = 1
                ORDER BY nombre
                """);
    }


    @GetMapping("/empleados")
    public List<Map<String, Object>> empleadosOperaciones() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_empleado,
                    codigo_empleado,
                    nombre_completo,
                    cargo,
                    COALESCE(es_tecnico,0) AS es_tecnico
                FROM dbo.vw_personal_control
                WHERE Estado_actividad = 1
                ORDER BY
                    COALESCE(es_tecnico,0),
                    nombre_completo
                """);
    }


    @GetMapping("/insumos")
    public List<Map<String, Object>> insumos() {
        return jdbcTemplate.queryForList("""
                SELECT
                    s.id_insumo AS id_articulo,
                    s.nombre_insumo AS nombre,
                    s.unidad_medida,
                    s.costo_promedio AS precio_referencia,
                    COALESCE(inv.stock_actual,0) AS stock_actual
                FROM dbo.insumos s
                OUTER APPLY (
                    SELECT TOP 1 i.stock_actual
                    FROM dbo.inventario i
                    INNER JOIN dbo.almacenes a
                        ON a.id_almacen = i.id_almacen
                    WHERE i.id_insumo = s.id_insumo
                      AND a.codigo_almacen = N'ALM-PRINCIPAL'
                      AND a.activo = 1
                ) inv
                WHERE s.activo = 1
                ORDER BY s.nombre_insumo
                """);
    }


    @GetMapping("/productos")
    public List<Map<String, Object>> productos() {
        /*
         * Ventas utiliza exactamente los mismos artículos que Inventario.
         * En la interfaz seguimos llamándolos "productos", pero la fuente
         * real es dbo.insumos + dbo.inventario (bodega principal).
         */
        return jdbcTemplate.queryForList("""
                SELECT
                    s.id_insumo AS id_articulo,
                    s.nombre_insumo AS nombre,
                    s.unidad_medida,
                    COALESCE(s.costo_promedio,0) AS precio_referencia,
                    COALESCE(inv.stock_actual,0) AS stock_actual
                FROM dbo.insumos s
                INNER JOIN dbo.inventario inv
                    ON inv.id_insumo = s.id_insumo
                INNER JOIN dbo.almacenes a
                    ON a.id_almacen = inv.id_almacen
                WHERE s.activo = 1
                  AND a.activo = 1
                  AND a.codigo_almacen = N'ALM-PRINCIPAL'
                  AND COALESCE(inv.stock_actual,0) > 0
                ORDER BY s.nombre_insumo
                """);
    }

    @GetMapping("/cajas")
    public List<Map<String, Object>> cajas() {
        return jdbcTemplate.queryForList("""
                SELECT
                    id_caja,
                    nombre_caja,
                    saldo_actual
                FROM dbo.cajas
                WHERE activo = 1
                ORDER BY nombre_caja
                """);
    }


    @GetMapping("/cuentas")
    public List<Map<String, Object>> cuentas() {
        return jdbcTemplate.queryForList("""
                SELECT
                    id_cuenta,
                    nombre_banco,
                    numero_cuenta,
                    tipo_cuenta,
                    moneda,
                    saldo_actual
                FROM dbo.cuentas_bancarias
                WHERE activo = 1
                ORDER BY nombre_banco, numero_cuenta
                """);
    }


    @GetMapping("/metodos-pago")
    public List<Map<String, Object>> metodosPago() {

        List<Map<String, Object>> resultado = new ArrayList<>();

        for (String metodo : List.of("Efectivo", "Transferencia", "Tarjeta", "Cheque")) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("metodo_pago", metodo);
            resultado.add(fila);
        }

        return resultado;
    }


    /* =========================================================
       DETALLE DE OPERACIÓN
       ========================================================= */

    @GetMapping("/operaciones/{tipo}/{id}")
    public Map<String, Object> detalleOperacion(
            @PathVariable String tipo,
            @PathVariable Integer id) {

        if ("COMPRA_INSUMO".equalsIgnoreCase(tipo)) {
            return detalleCompra(id);
        }

        if ("VENTA_PRODUCTO".equalsIgnoreCase(tipo)) {
            return detalleVenta(id);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tipo de operación no válido."
        );
    }


    private Map<String, Object> detalleCompra(Integer idCompra) {

        List<Map<String, Object>> cabeceras = jdbcTemplate.queryForList("""
                SELECT
                    c.id_compra AS id_operacion,
                    N'COMPRA_INSUMO' AS tipo_operacion,
                    c.fecha_compra AS fecha,
                    c.numero_factura AS documento,
                    c.estado,
                    c.subtotal,
                    c.impuesto,
                    c.descuento,
                    c.total,
                    c.codigo_moneda,
                    c.observaciones,
                    c.id_proveedor AS tercero_id,
                    p.nombre AS tercero,
                    c.id_empleado AS empleado_id,
                    emp.nombre_completo AS empleado,
                    emp.codigo_empleado AS codigo_empleado,
                    pg.metodo_pago
                FROM dbo.compras c
                INNER JOIN dbo.proveedores p
                    ON p.id_proveedor = c.id_proveedor
                LEFT JOIN dbo.vw_personal_control emp
                    ON emp.id_empleado = c.id_empleado
                OUTER APPLY (
                    SELECT TOP 1 metodo_pago
                    FROM dbo.pagos
                    WHERE id_compra = c.id_compra
                      AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                    ORDER BY id_pago DESC
                ) pg
                WHERE c.id_compra = ?
                """,
                idCompra
        );

        if (cabeceras.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Compra no encontrada."
            );
        }

        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT
                    dc.id_detalle,
                    dc.id_insumo AS id_articulo,
                    i.nombre_insumo AS articulo,
                    i.unidad_medida,
                    dc.cantidad,
                    dc.precio_unitario,
                    dc.descuento,
                    dc.subtotal
                FROM dbo.detalle_compras dc
                INNER JOIN dbo.insumos i
                    ON i.id_insumo = dc.id_insumo
                WHERE dc.id_compra = ?
                ORDER BY dc.id_detalle
                """,
                idCompra
        );

        Map<String, Object> respuesta =
                new LinkedHashMap<>(cabeceras.get(0));

        respuesta.put("items", items);
        return respuesta;
    }


    private Map<String, Object> detalleVenta(Integer idVenta) {

        List<Map<String, Object>> cabeceras = jdbcTemplate.queryForList("""
                SELECT
                    v.id_venta AS id_operacion,
                    N'VENTA_PRODUCTO' AS tipo_operacion,
                    v.fecha_venta AS fecha,
                    v.numero_factura AS documento,
                    v.estado,
                    v.subtotal,
                    v.impuesto,
                    v.descuento,
                    v.total,
                    v.codigo_moneda,
                    v.observaciones,
                    v.id_cliente AS tercero_id,
                    c.nombre AS tercero,
                    v.id_empleado AS empleado_id,
                    emp.nombre_completo AS empleado,
                    emp.codigo_empleado AS codigo_empleado,
                    pg.metodo_pago
                FROM dbo.ventas v
                INNER JOIN dbo.clientes c
                    ON c.id_cliente = v.id_cliente
                LEFT JOIN dbo.vw_personal_control emp
                    ON emp.id_empleado = v.id_empleado
                OUTER APPLY (
                    SELECT TOP 1 metodo_pago
                    FROM dbo.pagos
                    WHERE id_venta = v.id_venta
                      AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                    ORDER BY id_pago DESC
                ) pg
                WHERE v.id_venta = ?
                """,
                idVenta
        );

        if (cabeceras.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Venta no encontrada."
            );
        }

        /*
         * Nuevas ventas: id_insumo.
         * id_producto se conserva solo por compatibilidad con ventas antiguas.
         */
        List<Map<String, Object>> items = jdbcTemplate.queryForList("""
                SELECT
                    dv.id_detalle,
                    COALESCE(dv.id_insumo, dv.id_producto) AS id_articulo,
                    COALESCE(i.nombre_insumo, p.nombre_producto) AS articulo,
                    COALESCE(i.unidad_medida, p.unidad_medida) AS unidad_medida,
                    dv.cantidad,
                    dv.precio_unitario,
                    dv.descuento,
                    dv.subtotal
                FROM dbo.detalle_ventas dv
                LEFT JOIN dbo.insumos i
                    ON i.id_insumo = dv.id_insumo
                LEFT JOIN dbo.productos p
                    ON p.id_producto = dv.id_producto
                WHERE dv.id_venta = ?
                ORDER BY dv.id_detalle
                """,
                idVenta
        );

        Map<String, Object> respuesta =
                new LinkedHashMap<>(cabeceras.get(0));

        respuesta.put("items", items);
        return respuesta;
    }


    /* =========================================================
       REGISTRAR COMPRA
       ========================================================= */

    @PostMapping("/compras")
    @Transactional
    public Map<String, Object> registrarCompra(
            @RequestBody Map<String, Object> datos) {

        Integer idProveedor = entero(datos.get("terceroId"));
        String factura = textoOpcional(datos.get("numeroFactura"));
        String observaciones = textoOpcional(datos.get("observaciones"));
        String metodoPago = metodoPago(datos.get("metodoPago"));
        BigDecimal impuesto = decimalNoNegativo(datos.get("impuesto"));
        BigDecimal descuento = decimalNoNegativo(datos.get("descuento"));
        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));
        List<ItemOperacion> items = leerItems(datos.get("items"));

        validarProveedor(idProveedor);
        validarItems(items);

        BigDecimal subtotal = calcularSubtotal(items);
        BigDecimal total = calcularTotal(subtotal, impuesto, descuento);

        Integer idCompra = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.compras (
                    fecha_compra,
                    fecha_entrega,
                    estado,
                    subtotal,
                    impuesto,
                    descuento,
                    total,
                    codigo_moneda,
                    numero_factura,
                    observaciones,
                    id_proveedor,
                    id_empleado
                )
                OUTPUT INSERTED.id_compra
                VALUES (
                    CONVERT(date,GETDATE()),
                    CONVERT(date,GETDATE()),
                    N'Recibido',
                    ?, ?, ?, ?,
                    N'NIO',
                    ?, ?,
                    ?, ?
                )
                """,
                Integer.class,
                subtotal,
                impuesto,
                descuento,
                total,
                factura,
                observaciones,
                idProveedor,
                idEmpleado
        );

        for (ItemOperacion item : items) {
            insertarDetalleCompra(idCompra, item);
            sumarInventarioCompra(
                    item.idArticulo(),
                    item.cantidad(),
                    item.precioUnitario(),
                    idEmpleado,
                    "COMP-" + idCompra,
                    "Compra de producto"
            );
        }

        registrarPagoCompra(
                idCompra,
                total,
                metodoPago,
                factura,
                idEmpleado
        );

        return respuestaOk(
                "Compra registrada correctamente.",
                idCompra
        );
    }


    /* =========================================================
       MODIFICAR COMPRA
       ========================================================= */

    @PutMapping("/compras/{idCompra}")
    @Transactional
    public Map<String, Object> modificarCompra(
            @PathVariable Integer idCompra,
            @RequestBody Map<String, Object> datos) {

        validarCompraEditable(idCompra);

        Integer idProveedor = entero(datos.get("terceroId"));
        String factura = textoOpcional(datos.get("numeroFactura"));
        String observaciones = textoOpcional(datos.get("observaciones"));
        String metodoPago = metodoPago(datos.get("metodoPago"));
        BigDecimal impuesto = decimalNoNegativo(datos.get("impuesto"));
        BigDecimal descuento = decimalNoNegativo(datos.get("descuento"));
        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));
        List<ItemOperacion> nuevosItems = leerItems(datos.get("items"));

        validarProveedor(idProveedor);
        validarItems(nuevosItems);

        List<Map<String, Object>> anteriores = jdbcTemplate.queryForList("""
                SELECT
                    id_insumo,
                    cantidad,
                    precio_unitario
                FROM dbo.detalle_compras
                WHERE id_compra = ?
                """,
                idCompra
        );

        /* Primero revertimos la compra anterior. */
        for (Map<String, Object> anterior : anteriores) {
            Integer idInsumo = ((Number) anterior.get("id_insumo")).intValue();
            BigDecimal cantidad = numeroDecimal(anterior.get("cantidad"));
            BigDecimal precio = numeroDecimal(anterior.get("precio_unitario"));

            restarInventarioCompra(
                    idInsumo,
                    cantidad,
                    precio,
                    idEmpleado,
                    "MOD-COMP-" + idCompra,
                    "Reversión por modificación de compra"
            );
        }

        jdbcTemplate.update(
                "DELETE FROM dbo.detalle_compras WHERE id_compra = ?",
                idCompra
        );

        BigDecimal subtotal = calcularSubtotal(nuevosItems);
        BigDecimal total = calcularTotal(subtotal, impuesto, descuento);

        jdbcTemplate.update("""
                UPDATE dbo.compras
                SET
                    subtotal = ?,
                    impuesto = ?,
                    descuento = ?,
                    total = ?,
                    numero_factura = ?,
                    observaciones = ?,
                    id_proveedor = ?,
                    id_empleado = ?
                WHERE id_compra = ?
                """,
                subtotal,
                impuesto,
                descuento,
                total,
                factura,
                observaciones,
                idProveedor,
                idEmpleado,
                idCompra
        );

        for (ItemOperacion item : nuevosItems) {
            insertarDetalleCompra(idCompra, item);
            sumarInventarioCompra(
                    item.idArticulo(),
                    item.cantidad(),
                    item.precioUnitario(),
                    idEmpleado,
                    "MOD-COMP-" + idCompra,
                    "Compra modificada"
            );
        }

        actualizarPagoCompra(
                idCompra,
                total,
                metodoPago,
                factura,
                idEmpleado
        );

        return respuestaOk(
                "Compra modificada correctamente.",
                idCompra
        );
    }


    /* =========================================================
       ANULAR COMPRA
       ========================================================= */

    @PostMapping("/compras/{idCompra}/anular")
    @Transactional
    public Map<String, Object> anularCompra(
            @PathVariable Integer idCompra,
            @RequestBody Map<String, Object> datos) {

        validarCompraEditable(idCompra);

        String motivo = texto(datos.get("motivo"));

        if (motivo == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Indica el motivo de la anulación."
            );
        }

        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));

        List<Map<String, Object>> detalles = jdbcTemplate.queryForList("""
                SELECT
                    id_insumo,
                    cantidad,
                    precio_unitario
                FROM dbo.detalle_compras
                WHERE id_compra = ?
                """,
                idCompra
        );

        for (Map<String, Object> detalle : detalles) {
            restarInventarioCompra(
                    ((Number) detalle.get("id_insumo")).intValue(),
                    numeroDecimal(detalle.get("cantidad")),
                    numeroDecimal(detalle.get("precio_unitario")),
                    idEmpleado,
                    "ANUL-COMP-" + idCompra,
                    "Anulación de compra"
            );
        }

        jdbcTemplate.update("""
                UPDATE dbo.compras
                SET
                    estado = N'Cancelado',
                    fecha_anulacion = SYSDATETIME(),
                    motivo_anulacion = ?
                WHERE id_compra = ?
                """,
                motivo,
                idCompra
        );

        jdbcTemplate.update("""
                UPDATE dbo.pagos
                SET
                    estado = N'ANULADO',
                    fecha_anulacion = SYSDATETIME(),
                    motivo_anulacion = ?
                WHERE id_compra = ?
                  AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                """,
                motivo,
                idCompra
        );

        return respuestaOk(
                "Compra anulada correctamente.",
                idCompra
        );
    }


    /* =========================================================
       REGISTRAR VENTA
       Ventas descuenta del MISMO inventario usado por Compras.
       ========================================================= */

    @PostMapping("/ventas")
    @Transactional
    public Map<String, Object> registrarVenta(
            @RequestBody Map<String, Object> datos) {

        Integer idCliente = entero(datos.get("terceroId"));
        String factura = textoOpcional(datos.get("numeroFactura"));
        String observaciones = textoOpcional(datos.get("observaciones"));
        String metodoPago = metodoPago(datos.get("metodoPago"));
        BigDecimal impuesto = decimalNoNegativo(datos.get("impuesto"));
        BigDecimal descuento = decimalNoNegativo(datos.get("descuento"));
        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));
        List<ItemOperacion> items = leerItems(datos.get("items"));

        validarCliente(idCliente);
        validarItems(items);
        validarStockInsumos(items);

        BigDecimal subtotal = calcularSubtotal(items);
        BigDecimal total = calcularTotal(subtotal, impuesto, descuento);

        Integer idVenta = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.ventas (
                    fecha_venta,
                    estado,
                    subtotal,
                    impuesto,
                    descuento,
                    total,
                    codigo_moneda,
                    numero_factura,
                    observaciones,
                    id_cliente,
                    id_empleado
                )
                OUTPUT INSERTED.id_venta
                VALUES (
                    CONVERT(date,GETDATE()),
                    N'Completada',
                    ?, ?, ?, ?,
                    N'NIO',
                    ?, ?,
                    ?, ?
                )
                """,
                Integer.class,
                subtotal,
                impuesto,
                descuento,
                total,
                factura,
                observaciones,
                idCliente,
                idEmpleado
        );

        for (ItemOperacion item : items) {
            insertarDetalleVenta(idVenta, item);

            restarInventarioVenta(
                    item.idArticulo(),
                    item.cantidad(),
                    idEmpleado,
                    "VENT-" + idVenta,
                    "Venta de producto"
            );
        }

        registrarPagoVenta(
                idVenta,
                total,
                metodoPago,
                factura,
                idEmpleado
        );

        return respuestaOk(
                "Venta registrada correctamente.",
                idVenta
        );
    }


    /* =========================================================
       MODIFICAR VENTA
       ========================================================= */

    @PutMapping("/ventas/{idVenta}")
    @Transactional
    public Map<String, Object> modificarVenta(
            @PathVariable Integer idVenta,
            @RequestBody Map<String, Object> datos) {

        validarVentaEditable(idVenta);

        Integer idCliente = entero(datos.get("terceroId"));
        String factura = textoOpcional(datos.get("numeroFactura"));
        String observaciones = textoOpcional(datos.get("observaciones"));
        String metodoPago = metodoPago(datos.get("metodoPago"));
        BigDecimal impuesto = decimalNoNegativo(datos.get("impuesto"));
        BigDecimal descuento = decimalNoNegativo(datos.get("descuento"));
        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));
        List<ItemOperacion> nuevosItems = leerItems(datos.get("items"));

        validarCliente(idCliente);
        validarItems(nuevosItems);

        List<Map<String, Object>> anteriores = jdbcTemplate.queryForList("""
                SELECT
                    id_insumo,
                    id_producto,
                    cantidad
                FROM dbo.detalle_ventas
                WHERE id_venta = ?
                """,
                idVenta
        );

        /*
         * Reponemos temporalmente al stock lo que contenía la venta anterior.
         * Soporta también registros antiguos que todavía usen id_producto.
         */
        for (Map<String, Object> anterior : anteriores) {

            if (anterior.get("id_insumo") != null) {
                sumarInventarioVenta(
                        ((Number) anterior.get("id_insumo")).intValue(),
                        numeroDecimal(anterior.get("cantidad")),
                        idEmpleado,
                        "MOD-VENT-" + idVenta,
                        "Reversión por modificación de venta"
                );
            } else if (anterior.get("id_producto") != null) {
                sumarStockProductoLegacy(
                        ((Number) anterior.get("id_producto")).intValue(),
                        numeroDecimal(anterior.get("cantidad"))
                );
            }
        }

        validarStockInsumos(nuevosItems);

        jdbcTemplate.update(
                "DELETE FROM dbo.detalle_ventas WHERE id_venta = ?",
                idVenta
        );

        BigDecimal subtotal = calcularSubtotal(nuevosItems);
        BigDecimal total = calcularTotal(subtotal, impuesto, descuento);

        jdbcTemplate.update("""
                UPDATE dbo.ventas
                SET
                    subtotal = ?,
                    impuesto = ?,
                    descuento = ?,
                    total = ?,
                    numero_factura = ?,
                    observaciones = ?,
                    id_cliente = ?,
                    id_empleado = ?
                WHERE id_venta = ?
                """,
                subtotal,
                impuesto,
                descuento,
                total,
                factura,
                observaciones,
                idCliente,
                idEmpleado,
                idVenta
        );

        for (ItemOperacion item : nuevosItems) {
            insertarDetalleVenta(idVenta, item);

            restarInventarioVenta(
                    item.idArticulo(),
                    item.cantidad(),
                    idEmpleado,
                    "MOD-VENT-" + idVenta,
                    "Venta modificada"
            );
        }

        actualizarPagoVenta(
                idVenta,
                total,
                metodoPago,
                factura,
                idEmpleado
        );

        return respuestaOk(
                "Venta modificada correctamente.",
                idVenta
        );
    }


    /* =========================================================
       ANULAR VENTA
       ========================================================= */

    @PostMapping("/ventas/{idVenta}/anular")
    @Transactional
    public Map<String, Object> anularVenta(
            @PathVariable Integer idVenta,
            @RequestBody Map<String, Object> datos) {

        validarVentaEditable(idVenta);

        String motivo = texto(datos.get("motivo"));

        if (motivo == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Indica el motivo de la anulación."
            );
        }

        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));

        List<Map<String, Object>> detalles = jdbcTemplate.queryForList("""
                SELECT
                    id_insumo,
                    id_producto,
                    cantidad
                FROM dbo.detalle_ventas
                WHERE id_venta = ?
                """,
                idVenta
        );

        for (Map<String, Object> detalle : detalles) {

            if (detalle.get("id_insumo") != null) {
                sumarInventarioVenta(
                        ((Number) detalle.get("id_insumo")).intValue(),
                        numeroDecimal(detalle.get("cantidad")),
                        idEmpleado,
                        "ANUL-VENT-" + idVenta,
                        "Anulación de venta"
                );
            } else if (detalle.get("id_producto") != null) {
                sumarStockProductoLegacy(
                        ((Number) detalle.get("id_producto")).intValue(),
                        numeroDecimal(detalle.get("cantidad"))
                );
            }
        }

        jdbcTemplate.update("""
                UPDATE dbo.ventas
                SET
                    estado = N'Anulada',
                    fecha_anulacion = SYSDATETIME(),
                    motivo_anulacion = ?
                WHERE id_venta = ?
                """,
                motivo,
                idVenta
        );

        jdbcTemplate.update("""
                UPDATE dbo.pagos
                SET
                    estado = N'ANULADO',
                    fecha_anulacion = SYSDATETIME(),
                    motivo_anulacion = ?
                WHERE id_venta = ?
                  AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                """,
                motivo,
                idVenta
        );

        return respuestaOk(
                "Venta anulada correctamente.",
                idVenta
        );
    }

    /* =========================================================
       HISTORIAL DE DEVOLUCIONES
       ========================================================= */

    @GetMapping("/devoluciones")
    public List<Map<String, Object>> devoluciones() {

        return jdbcTemplate.queryForList("""
                SELECT
                    d.id_devolucion,
                    d.fecha,
                    d.tipo_devolucion,
                    d.monto,
                    d.motivo,
                    d.estado,
                    d.observaciones,

                    CASE
                        WHEN d.id_venta IS NOT NULL THEN N'VENTA_PRODUCTO'
                        WHEN d.id_compra IS NOT NULL THEN N'COMPRA_INSUMO'
                        ELSE N'SIN_ORIGEN'
                    END AS tipo_operacion_origen,

                    COALESCE(v.numero_factura, co.numero_factura) AS documento,

                    COALESCE(
                        cli.nombre,
                        prov.nombre,
                        N'Sin tercero'
                    ) AS tercero,

                    COALESCE(
                        prod.nombre_producto,
                        ins.nombre_insumo,
                        N'Producto no especificado'
                    ) AS producto,

                    dd.cantidad,
                    dd.precio_unitario,
                    dd.subtotal AS subtotal_devuelto

                FROM dbo.devoluciones d

                LEFT JOIN dbo.detalle_devoluciones dd
                    ON dd.id_devolucion = d.id_devolucion

                LEFT JOIN dbo.productos prod
                    ON prod.id_producto = dd.id_producto

                LEFT JOIN dbo.insumos ins
                    ON ins.id_insumo = dd.id_insumo

                LEFT JOIN dbo.ventas v
                    ON v.id_venta = d.id_venta

                LEFT JOIN dbo.clientes cli
                    ON cli.id_cliente = v.id_cliente

                LEFT JOIN dbo.compras co
                    ON co.id_compra = d.id_compra

                LEFT JOIN dbo.proveedores prov
                    ON prov.id_proveedor = co.id_proveedor

                WHERE
                    d.id_venta IS NOT NULL
                    OR d.id_compra IS NOT NULL

                ORDER BY
                    d.fecha DESC,
                    d.id_devolucion DESC,
                    dd.id_detalle_devolucion
                """);
    }


    @GetMapping("/devoluciones/opciones/{tipo}/{idOperacion}")
    public List<Map<String, Object>> opcionesDevolucion(
            @PathVariable String tipo,
            @PathVariable Integer idOperacion) {

        if ("VENTA_PRODUCTO".equalsIgnoreCase(tipo)) {

            /*
             * Nuevas ventas usan id_insumo. La segunda parte del UNION
             * mantiene compatibilidad con posibles ventas antiguas.
             */
            return jdbcTemplate.queryForList("""
                    SELECT *
                    FROM (
                        SELECT
                            dv.id_detalle,
                            dv.id_insumo AS id_articulo,
                            i.nombre_insumo AS producto,
                            dv.cantidad AS cantidad_original,
                            dv.precio_unitario,
                            CAST(COALESCE(dev.cantidad_devuelta,0) AS DECIMAL(10,2))
                                AS cantidad_devuelta,
                            CAST(
                                dv.cantidad - COALESCE(dev.cantidad_devuelta,0)
                                AS DECIMAL(10,2)
                            ) AS cantidad_disponible
                        FROM dbo.detalle_ventas dv
                        INNER JOIN dbo.insumos i
                            ON i.id_insumo = dv.id_insumo
                        INNER JOIN dbo.ventas v
                            ON v.id_venta = dv.id_venta
                        OUTER APPLY (
                            SELECT SUM(dd.cantidad) AS cantidad_devuelta
                            FROM dbo.devoluciones d
                            INNER JOIN dbo.detalle_devoluciones dd
                                ON dd.id_devolucion = d.id_devolucion
                            WHERE d.id_venta = dv.id_venta
                              AND dd.id_insumo = dv.id_insumo
                              AND d.estado = N'APLICADA'
                        ) dev
                        WHERE dv.id_venta = ?
                          AND dv.id_insumo IS NOT NULL
                          AND v.estado <> N'Anulada'
                          AND dv.cantidad - COALESCE(dev.cantidad_devuelta,0) > 0

                        UNION ALL

                        SELECT
                            dv.id_detalle,
                            dv.id_producto AS id_articulo,
                            p.nombre_producto AS producto,
                            dv.cantidad AS cantidad_original,
                            dv.precio_unitario,
                            CAST(COALESCE(dev.cantidad_devuelta,0) AS DECIMAL(10,2))
                                AS cantidad_devuelta,
                            CAST(
                                dv.cantidad - COALESCE(dev.cantidad_devuelta,0)
                                AS DECIMAL(10,2)
                            ) AS cantidad_disponible
                        FROM dbo.detalle_ventas dv
                        INNER JOIN dbo.productos p
                            ON p.id_producto = dv.id_producto
                        INNER JOIN dbo.ventas v
                            ON v.id_venta = dv.id_venta
                        OUTER APPLY (
                            SELECT SUM(dd.cantidad) AS cantidad_devuelta
                            FROM dbo.devoluciones d
                            INNER JOIN dbo.detalle_devoluciones dd
                                ON dd.id_devolucion = d.id_devolucion
                            WHERE d.id_venta = dv.id_venta
                              AND dd.id_producto = dv.id_producto
                              AND d.estado = N'APLICADA'
                        ) dev
                        WHERE dv.id_venta = ?
                          AND dv.id_producto IS NOT NULL
                          AND v.estado <> N'Anulada'
                          AND dv.cantidad - COALESCE(dev.cantidad_devuelta,0) > 0
                    ) x
                    ORDER BY producto
                    """,
                    idOperacion,
                    idOperacion
            );
        }

        if ("COMPRA_INSUMO".equalsIgnoreCase(tipo)) {

            return jdbcTemplate.queryForList("""
                    SELECT
                        dc.id_detalle,
                        dc.id_insumo AS id_articulo,
                        i.nombre_insumo AS producto,
                        dc.cantidad AS cantidad_original,
                        dc.precio_unitario,
                        CAST(COALESCE(dev.cantidad_devuelta,0) AS DECIMAL(10,2))
                            AS cantidad_devuelta,
                        CAST(
                            dc.cantidad - COALESCE(dev.cantidad_devuelta,0)
                            AS DECIMAL(10,2)
                        ) AS cantidad_disponible
                    FROM dbo.detalle_compras dc
                    INNER JOIN dbo.insumos i
                        ON i.id_insumo = dc.id_insumo
                    INNER JOIN dbo.compras c
                        ON c.id_compra = dc.id_compra
                    OUTER APPLY (
                        SELECT SUM(dd.cantidad) AS cantidad_devuelta
                        FROM dbo.devoluciones d
                        INNER JOIN dbo.detalle_devoluciones dd
                            ON dd.id_devolucion = d.id_devolucion
                        WHERE d.id_compra = dc.id_compra
                          AND dd.id_insumo = dc.id_insumo
                          AND d.estado = N'APLICADA'
                    ) dev
                    WHERE dc.id_compra = ?
                      AND c.estado <> N'Cancelado'
                      AND dc.cantidad - COALESCE(dev.cantidad_devuelta,0) > 0
                    ORDER BY i.nombre_insumo
                    """,
                    idOperacion
            );
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tipo de operación no válido para devolución."
        );
    }

    @PostMapping("/devoluciones/producto")
    @Transactional
    public Map<String, Object> registrarDevolucionProducto(
            @RequestBody Map<String, Object> datos) {

        String tipoOperacion = texto(datos.get("tipoOperacion"));
        Integer idOperacion = entero(datos.get("idOperacion"));
        Integer idDetalle = entero(datos.get("idDetalle"));
        BigDecimal cantidad = decimal(datos.get("cantidad"));
        String motivo = texto(datos.get("motivo"));
        String observaciones = textoOpcional(datos.get("observaciones"));
        String metodoReembolso = metodoPago(datos.get("metodoPago"));
        Integer idEmpleado = resolverEmpleado(datos.get("idEmpleado"));

        if (tipoOperacion == null ||
            idOperacion == null ||
            idDetalle == null ||
            cantidad == null ||
            cantidad.compareTo(BigDecimal.ZERO) <= 0 ||
            motivo == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Completa correctamente los datos de la devolución."
            );
        }

        if (motivo.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El motivo no puede superar los 150 caracteres."
            );
        }

        if ("VENTA_PRODUCTO".equalsIgnoreCase(tipoOperacion)) {
            return registrarDevolucionVenta(
                    idOperacion,
                    idDetalle,
                    cantidad,
                    motivo,
                    observaciones,
                    metodoReembolso,
                    idEmpleado
            );
        }

        if ("COMPRA_INSUMO".equalsIgnoreCase(tipoOperacion)) {
            return registrarDevolucionCompra(
                    idOperacion,
                    idDetalle,
                    cantidad,
                    motivo,
                    observaciones,
                    metodoReembolso,
                    idEmpleado
            );
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Solo se permiten devoluciones de compras o ventas de productos."
        );
    }


    private Map<String, Object> registrarDevolucionVenta(
            Integer idVenta,
            Integer idDetalle,
            BigDecimal cantidad,
            String motivo,
            String observaciones,
            String metodoReembolso,
            Integer idEmpleado) {

        List<Map<String, Object>> detalles = jdbcTemplate.queryForList("""
                SELECT
                    dv.id_insumo,
                    dv.id_producto,
                    dv.cantidad,
                    dv.precio_unitario,
                    v.codigo_moneda,
                    v.estado
                FROM dbo.detalle_ventas dv
                INNER JOIN dbo.ventas v
                    ON v.id_venta = dv.id_venta
                WHERE dv.id_detalle = ?
                  AND dv.id_venta = ?
                """,
                idDetalle,
                idVenta
        );

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El producto no pertenece a la venta seleccionada."
            );
        }

        Map<String, Object> detalle = detalles.get(0);

        if ("Anulada".equalsIgnoreCase(String.valueOf(detalle.get("estado")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede devolver una venta anulada."
            );
        }

        BigDecimal cantidadOriginal = numeroDecimal(detalle.get("cantidad"));
        BigDecimal precioUnitario = numeroDecimal(detalle.get("precio_unitario"));

        Integer idInsumo = detalle.get("id_insumo") == null
                ? null
                : ((Number) detalle.get("id_insumo")).intValue();

        Integer idProductoLegacy = detalle.get("id_producto") == null
                ? null
                : ((Number) detalle.get("id_producto")).intValue();

        BigDecimal yaDevuelta;

        if (idInsumo != null) {
            yaDevuelta = jdbcTemplate.queryForObject("""
                    SELECT CAST(COALESCE(SUM(dd.cantidad),0) AS DECIMAL(10,2))
                    FROM dbo.devoluciones d
                    INNER JOIN dbo.detalle_devoluciones dd
                        ON dd.id_devolucion = d.id_devolucion
                    WHERE d.id_venta = ?
                      AND dd.id_insumo = ?
                      AND d.estado = N'APLICADA'
                    """,
                    BigDecimal.class,
                    idVenta,
                    idInsumo
            );
        } else {
            yaDevuelta = jdbcTemplate.queryForObject("""
                    SELECT CAST(COALESCE(SUM(dd.cantidad),0) AS DECIMAL(10,2))
                    FROM dbo.devoluciones d
                    INNER JOIN dbo.detalle_devoluciones dd
                        ON dd.id_devolucion = d.id_devolucion
                    WHERE d.id_venta = ?
                      AND dd.id_producto = ?
                      AND d.estado = N'APLICADA'
                    """,
                    BigDecimal.class,
                    idVenta,
                    idProductoLegacy
            );
        }

        BigDecimal disponible = cantidadOriginal.subtract(
                yaDevuelta == null ? BigDecimal.ZERO : yaDevuelta
        );

        if (cantidad.compareTo(disponible) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cantidad supera lo disponible para devolver."
            );
        }

        BigDecimal subtotal = dinero(
                precioUnitario.multiply(cantidad)
        );

        Integer idDevolucion = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.devoluciones (
                    fecha,
                    monto,
                    motivo,
                    id_venta,
                    observaciones,
                    tipo_devolucion,
                    estado,
                    id_empleado
                )
                OUTPUT INSERTED.id_devolucion
                VALUES (
                    CONVERT(date,GETDATE()),
                    ?, ?, ?, ?,
                    N'VENTA_PRODUCTO',
                    N'APLICADA',
                    ?
                )
                """,
                Integer.class,
                subtotal,
                motivo,
                idVenta,
                observaciones,
                idEmpleado
        );

        if (idInsumo != null) {

            jdbcTemplate.update("""
                    INSERT INTO dbo.detalle_devoluciones (
                        id_devolucion,
                        id_insumo,
                        cantidad,
                        precio_unitario,
                        subtotal,
                        observaciones
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    idDevolucion,
                    idInsumo,
                    cantidad,
                    precioUnitario,
                    subtotal,
                    observaciones
            );

            sumarInventarioVenta(
                    idInsumo,
                    cantidad,
                    idEmpleado,
                    "DEV-VENT-" + idVenta,
                    "Devolución de venta"
            );

        } else {

            jdbcTemplate.update("""
                    INSERT INTO dbo.detalle_devoluciones (
                        id_devolucion,
                        id_producto,
                        cantidad,
                        precio_unitario,
                        subtotal,
                        observaciones
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    idDevolucion,
                    idProductoLegacy,
                    cantidad,
                    precioUnitario,
                    subtotal,
                    observaciones
            );

            sumarStockProductoLegacy(
                    idProductoLegacy,
                    cantidad
            );
        }

        jdbcTemplate.update("""
                INSERT INTO dbo.pagos (
                    fecha_pago,
                    monto,
                    codigo_moneda,
                    referencia,
                    tipo_pago,
                    id_devolucion,
                    id_empleado,
                    metodo_pago,
                    estado,
                    observaciones
                )
                VALUES (
                    CONVERT(date,GETDATE()),
                    ?,
                    ?,
                    ?,
                    N'DEVOLUCION_VENTA',
                    ?,
                    ?,
                    ?,
                    N'APLICADO',
                    N'Reembolso por devolución de venta'
                )
                """,
                subtotal,
                detalle.get("codigo_moneda"),
                "DEV-" + idDevolucion,
                idDevolucion,
                idEmpleado,
                metodoReembolso
        );

        return respuestaOk(
                "Devolución de venta registrada correctamente.",
                idDevolucion
        );
    }

    private Map<String, Object> registrarDevolucionCompra(
            Integer idCompra,
            Integer idDetalle,
            BigDecimal cantidad,
            String motivo,
            String observaciones,
            String metodoReembolso,
            Integer idEmpleado) {

        List<Map<String, Object>> detalles = jdbcTemplate.queryForList("""
                SELECT
                    dc.id_insumo,
                    dc.cantidad,
                    dc.precio_unitario,
                    c.codigo_moneda,
                    c.estado
                FROM dbo.detalle_compras dc
                INNER JOIN dbo.compras c
                    ON c.id_compra = dc.id_compra
                WHERE dc.id_detalle = ?
                  AND dc.id_compra = ?
                """,
                idDetalle,
                idCompra
        );

        if (detalles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El producto no pertenece a la compra seleccionada."
            );
        }

        Map<String, Object> detalle = detalles.get(0);

        if ("Cancelado".equalsIgnoreCase(String.valueOf(detalle.get("estado")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede devolver una compra anulada."
            );
        }

        Integer idInsumo = ((Number) detalle.get("id_insumo")).intValue();
        BigDecimal cantidadOriginal = numeroDecimal(detalle.get("cantidad"));
        BigDecimal precioUnitario = numeroDecimal(detalle.get("precio_unitario"));

        BigDecimal yaDevuelta = jdbcTemplate.queryForObject("""
                SELECT CAST(COALESCE(SUM(dd.cantidad),0) AS DECIMAL(10,2))
                FROM dbo.devoluciones d
                INNER JOIN dbo.detalle_devoluciones dd
                    ON dd.id_devolucion = d.id_devolucion
                WHERE d.id_compra = ?
                  AND dd.id_insumo = ?
                  AND d.estado = N'APLICADA'
                """,
                BigDecimal.class,
                idCompra,
                idInsumo
        );

        BigDecimal disponible = cantidadOriginal.subtract(
                yaDevuelta == null ? BigDecimal.ZERO : yaDevuelta
        );

        if (cantidad.compareTo(disponible) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cantidad supera lo disponible para devolver."
            );
        }

        BigDecimal subtotal = dinero(
                precioUnitario.multiply(cantidad)
        );

        /* Al devolver una compra, el insumo sale de la bodega. */
        restarInventarioCompra(
                idInsumo,
                cantidad,
                precioUnitario,
                idEmpleado,
                "DEV-COMP-" + idCompra,
                "Devolución al proveedor"
        );

        Integer idDevolucion = jdbcTemplate.queryForObject("""
                INSERT INTO dbo.devoluciones (
                    fecha,
                    monto,
                    motivo,
                    id_compra,
                    observaciones,
                    tipo_devolucion,
                    estado,
                    id_empleado
                )
                OUTPUT INSERTED.id_devolucion
                VALUES (
                    CONVERT(date,GETDATE()),
                    ?, ?, ?, ?,
                    N'COMPRA_INSUMO',
                    N'APLICADA',
                    ?
                )
                """,
                Integer.class,
                subtotal,
                motivo,
                idCompra,
                observaciones,
                idEmpleado
        );

        jdbcTemplate.update("""
                INSERT INTO dbo.detalle_devoluciones (
                    id_devolucion,
                    id_insumo,
                    cantidad,
                    precio_unitario,
                    subtotal,
                    observaciones
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                idDevolucion,
                idInsumo,
                cantidad,
                precioUnitario,
                subtotal,
                observaciones
        );

        jdbcTemplate.update("""
                INSERT INTO dbo.pagos (
                    fecha_pago,
                    monto,
                    codigo_moneda,
                    referencia,
                    tipo_pago,
                    id_devolucion,
                    id_empleado,
                    metodo_pago,
                    estado,
                    observaciones
                )
                VALUES (
                    CONVERT(date,GETDATE()),
                    ?,
                    ?,
                    ?,
                    N'DEVOLUCION_COMPRA',
                    ?,
                    ?,
                    ?,
                    N'APLICADO',
                    N'Reembolso recibido por devolución de compra'
                )
                """,
                subtotal,
                detalle.get("codigo_moneda"),
                "DEV-" + idDevolucion,
                idDevolucion,
                idEmpleado,
                metodoReembolso
        );

        return respuestaOk(
                "Devolución de compra registrada correctamente.",
                idDevolucion
        );
    }


    /* =========================================================
       VALIDACIONES Y HELPERS DE NEGOCIO
       ========================================================= */

    private void validarProveedor(Integer idProveedor) {
        if (idProveedor == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un proveedor."
            );
        }

        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.proveedores
                WHERE id_proveedor = ?
                  AND activo = 1
                """,
                Integer.class,
                idProveedor
        );

        if (cantidad == null || cantidad == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El proveedor seleccionado no está disponible."
            );
        }
    }


    private void validarCliente(Integer idCliente) {
        if (idCliente == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Selecciona un cliente."
            );
        }

        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.clientes
                WHERE id_cliente = ?
                  AND activo = 1
                """,
                Integer.class,
                idCliente
        );

        if (cantidad == null || cantidad == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El cliente seleccionado no está disponible."
            );
        }
    }


    private void validarCompraEditable(Integer idCompra) {

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT estado
                FROM dbo.compras
                WHERE id_compra = ?
                """,
                idCompra
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Compra no encontrada."
            );
        }

        if ("Cancelado".equalsIgnoreCase(String.valueOf(filas.get(0).get("estado")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La compra ya está anulada."
            );
        }

        if (tieneDevolucionesCompra(idCompra)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede modificar ni anular una compra que ya tiene devoluciones. El historial debe conservarse."
            );
        }
    }


    private void validarVentaEditable(Integer idVenta) {

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT estado
                FROM dbo.ventas
                WHERE id_venta = ?
                """,
                idVenta
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Venta no encontrada."
            );
        }

        if ("Anulada".equalsIgnoreCase(String.valueOf(filas.get(0).get("estado")))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La venta ya está anulada."
            );
        }

        if (tieneDevolucionesVenta(idVenta)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede modificar ni anular una venta que ya tiene devoluciones. El historial debe conservarse."
            );
        }
    }


    private boolean tieneDevolucionesCompra(Integer idCompra) {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.devoluciones
                WHERE id_compra = ?
                  AND estado = N'APLICADA'
                """,
                Integer.class,
                idCompra
        );

        return cantidad != null && cantidad > 0;
    }


    private boolean tieneDevolucionesVenta(Integer idVenta) {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.devoluciones
                WHERE id_venta = ?
                  AND estado = N'APLICADA'
                """,
                Integer.class,
                idVenta
        );

        return cantidad != null && cantidad > 0;
    }


    private Integer resolverEmpleado(Object valor) {

        Integer idSolicitado = entero(valor);

        if (idSolicitado != null) {
            Integer existe = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.empleados
                    WHERE id_empleado = ?
                      AND Estado_actividad = 1
                    """,
                    Integer.class,
                    idSolicitado
            );

            if (existe != null && existe > 0) {
                return idSolicitado;
            }
        }

        List<Integer> disponibles = jdbcTemplate.queryForList("""
                SELECT TOP 1 id_empleado
                FROM dbo.empleados
                WHERE Estado_actividad = 1
                ORDER BY id_empleado
                """,
                Integer.class
        );

        if (disponibles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No existe un empleado activo para registrar la operación."
            );
        }

        return disponibles.get(0);
    }


    private void validarItems(List<ItemOperacion> items) {

        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agrega al menos un producto."
            );
        }

        for (ItemOperacion item : items) {
            if (item.idArticulo() == null ||
                item.cantidad() == null ||
                item.precioUnitario() == null ||
                item.cantidad().compareTo(BigDecimal.ZERO) <= 0 ||
                item.precioUnitario().compareTo(BigDecimal.ZERO) < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Hay productos con cantidad o precio no válidos."
                );
            }
        }
    }


    private List<ItemOperacion> leerItems(Object valor) {

        if (!(valor instanceof List<?> lista)) {
            return List.of();
        }

        List<ItemOperacion> resultado = new ArrayList<>();

        for (Object elemento : lista) {
            if (!(elemento instanceof Map<?, ?> mapa)) {
                continue;
            }

            resultado.add(
                    new ItemOperacion(
                            entero(mapa.get("idArticulo")),
                            decimal(mapa.get("cantidad")),
                            decimal(mapa.get("precioUnitario"))
                    )
            );
        }

        return resultado;
    }


    private BigDecimal calcularSubtotal(List<ItemOperacion> items) {

        BigDecimal subtotal = BigDecimal.ZERO;

        for (ItemOperacion item : items) {
            subtotal = subtotal.add(
                    item.cantidad().multiply(item.precioUnitario())
            );
        }

        return dinero(subtotal);
    }


    private BigDecimal calcularTotal(
            BigDecimal subtotal,
            BigDecimal impuesto,
            BigDecimal descuento) {

        BigDecimal total = subtotal
                .add(impuesto)
                .subtract(descuento);

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El descuento no puede ser mayor al subtotal más el impuesto."
            );
        }

        return dinero(total);
    }


    private void validarStockInsumos(List<ItemOperacion> items) {

        Integer idAlmacen = bodegaPrincipal();

        for (ItemOperacion item : items) {

            List<BigDecimal> stock = jdbcTemplate.queryForList("""
                    SELECT CAST(COALESCE(inv.stock_actual,0) AS DECIMAL(10,2))
                    FROM dbo.inventario inv
                    INNER JOIN dbo.insumos i
                        ON i.id_insumo = inv.id_insumo
                    WHERE inv.id_almacen = ?
                      AND inv.id_insumo = ?
                      AND i.activo = 1
                    """,
                    BigDecimal.class,
                    idAlmacen,
                    item.idArticulo()
            );

            if (stock.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Uno de los productos seleccionados no existe en el inventario."
                );
            }

            if (stock.get(0).compareTo(item.cantidad()) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No hay suficiente stock para completar la venta."
                );
            }
        }
    }

    /* =========================================================
       INVENTARIO DE COMPRAS
       ========================================================= */

    private Integer bodegaPrincipal() {

        List<Integer> bodegas = jdbcTemplate.queryForList("""
                SELECT TOP 1 id_almacen
                FROM dbo.almacenes
                WHERE codigo_almacen = N'ALM-PRINCIPAL'
                  AND activo = 1
                """,
                Integer.class
        );

        if (bodegas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se encontró la bodega principal del inventario."
            );
        }

        return bodegas.get(0);
    }


    private void sumarInventarioCompra(
            Integer idInsumo,
            BigDecimal cantidad,
            BigDecimal costo,
            Integer idEmpleado,
            String referencia,
            String motivo) {

        Integer idAlmacen = bodegaPrincipal();

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT
                    id_inventario,
                    stock_actual
                FROM dbo.inventario
                WHERE id_almacen = ?
                  AND id_insumo = ?
                """,
                idAlmacen,
                idInsumo
        );

        BigDecimal anterior = BigDecimal.ZERO;
        BigDecimal nuevo;

        if (filas.isEmpty()) {

            nuevo = cantidad;

            jdbcTemplate.update("""
                    INSERT INTO dbo.inventario (
                        id_almacen,
                        id_insumo,
                        stock_actual,
                        fecha_ultima_actualizacion
                    )
                    VALUES (?, ?, ?, GETDATE())
                    """,
                    idAlmacen,
                    idInsumo,
                    nuevo
            );

        } else {

            anterior = numeroDecimal(filas.get(0).get("stock_actual"));
            nuevo = anterior.add(cantidad);

            jdbcTemplate.update("""
                    UPDATE dbo.inventario
                    SET
                        stock_actual = ?,
                        fecha_ultima_actualizacion = GETDATE()
                    WHERE id_inventario = ?
                    """,
                    nuevo,
                    filas.get(0).get("id_inventario")
            );
        }

        jdbcTemplate.update("""
                UPDATE dbo.insumos
                SET
                    costo_promedio = ?,
                    fecha_ingreso = COALESCE(fecha_ingreso,CONVERT(date,GETDATE()))
                WHERE id_insumo = ?
                """,
                costo,
                idInsumo
        );

        registrarMovimientoInventario(
                "Entrada",
                idAlmacen,
                idInsumo,
                cantidad,
                costo,
                anterior,
                nuevo,
                motivo,
                referencia,
                idEmpleado
        );
    }


    private void restarInventarioCompra(
            Integer idInsumo,
            BigDecimal cantidad,
            BigDecimal costo,
            Integer idEmpleado,
            String referencia,
            String motivo) {

        Integer idAlmacen = bodegaPrincipal();

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT
                    id_inventario,
                    stock_actual
                FROM dbo.inventario
                WHERE id_almacen = ?
                  AND id_insumo = ?
                """,
                idAlmacen,
                idInsumo
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El producto no existe en la bodega principal."
            );
        }

        BigDecimal anterior = numeroDecimal(
                filas.get(0).get("stock_actual")
        );

        if (anterior.compareTo(cantidad) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No hay suficiente stock para revertir esta compra."
            );
        }

        BigDecimal nuevo = anterior.subtract(cantidad);

        jdbcTemplate.update("""
                UPDATE dbo.inventario
                SET
                    stock_actual = ?,
                    fecha_ultima_actualizacion = GETDATE()
                WHERE id_inventario = ?
                """,
                nuevo,
                filas.get(0).get("id_inventario")
        );

        registrarMovimientoInventario(
                "Salida",
                idAlmacen,
                idInsumo,
                cantidad,
                costo,
                anterior,
                nuevo,
                motivo,
                referencia,
                idEmpleado
        );
    }


    private void registrarMovimientoInventario(
            String tipo,
            Integer idAlmacen,
            Integer idInsumo,
            BigDecimal cantidad,
            BigDecimal costo,
            BigDecimal anterior,
            BigDecimal nuevo,
            String motivo,
            String referencia,
            Integer idEmpleado) {

        jdbcTemplate.update("""
                INSERT INTO dbo.movimientos_inventario (
                    tipo_movimiento,
                    fecha,
                    id_almacen,
                    id_insumo,
                    cantidad,
                    costo_unitario,
                    saldo_anterior,
                    saldo_nuevo,
                    motivo,
                    referencia,
                    id_empleado
                )
                VALUES (
                    ?,
                    GETDATE(),
                    ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """,
                tipo,
                idAlmacen,
                idInsumo,
                cantidad,
                costo,
                anterior,
                nuevo,
                motivo,
                referencia,
                idEmpleado
        );
    }


    /* =========================================================
       INVENTARIO DE VENTAS
       Usa dbo.inventario + dbo.insumos, igual que Compras.
       ========================================================= */

    private void restarInventarioVenta(
            Integer idInsumo,
            BigDecimal cantidad,
            Integer idEmpleado,
            String referencia,
            String motivo) {

        Integer idAlmacen = bodegaPrincipal();

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT
                    inv.id_inventario,
                    inv.stock_actual,
                    COALESCE(i.costo_promedio,0) AS costo_promedio
                FROM dbo.inventario inv
                INNER JOIN dbo.insumos i
                    ON i.id_insumo = inv.id_insumo
                WHERE inv.id_almacen = ?
                  AND inv.id_insumo = ?
                  AND i.activo = 1
                """,
                idAlmacen,
                idInsumo
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El producto no existe en la bodega principal."
            );
        }

        BigDecimal anterior = numeroDecimal(
                filas.get(0).get("stock_actual")
        );

        if (anterior.compareTo(cantidad) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No hay suficiente stock para completar la venta."
            );
        }

        BigDecimal nuevo = anterior.subtract(cantidad);
        BigDecimal costo = numeroDecimal(
                filas.get(0).get("costo_promedio")
        );

        jdbcTemplate.update("""
                UPDATE dbo.inventario
                SET
                    stock_actual = ?,
                    fecha_ultima_actualizacion = GETDATE()
                WHERE id_inventario = ?
                """,
                nuevo,
                filas.get(0).get("id_inventario")
        );

        registrarMovimientoInventario(
                "Salida",
                idAlmacen,
                idInsumo,
                cantidad,
                costo,
                anterior,
                nuevo,
                motivo,
                referencia,
                idEmpleado
        );
    }


    private void sumarInventarioVenta(
            Integer idInsumo,
            BigDecimal cantidad,
            Integer idEmpleado,
            String referencia,
            String motivo) {

        Integer idAlmacen = bodegaPrincipal();

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT
                    inv.id_inventario,
                    inv.stock_actual,
                    COALESCE(i.costo_promedio,0) AS costo_promedio
                FROM dbo.inventario inv
                INNER JOIN dbo.insumos i
                    ON i.id_insumo = inv.id_insumo
                WHERE inv.id_almacen = ?
                  AND inv.id_insumo = ?
                """,
                idAlmacen,
                idInsumo
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El producto no existe en la bodega principal."
            );
        }

        BigDecimal anterior = numeroDecimal(
                filas.get(0).get("stock_actual")
        );

        BigDecimal nuevo = anterior.add(cantidad);
        BigDecimal costo = numeroDecimal(
                filas.get(0).get("costo_promedio")
        );

        jdbcTemplate.update("""
                UPDATE dbo.inventario
                SET
                    stock_actual = ?,
                    fecha_ultima_actualizacion = GETDATE()
                WHERE id_inventario = ?
                """,
                nuevo,
                filas.get(0).get("id_inventario")
        );

        registrarMovimientoInventario(
                "Entrada",
                idAlmacen,
                idInsumo,
                cantidad,
                costo,
                anterior,
                nuevo,
                motivo,
                referencia,
                idEmpleado
        );
    }


    /*
     * Compatibilidad con ventas antiguas que pudieran existir
     * antes de unificar Ventas con Inventario.
     */
    private void sumarStockProductoLegacy(
            Integer idProducto,
            BigDecimal cantidad) {

        if (idProducto == null) return;

        jdbcTemplate.update("""
                UPDATE dbo.productos
                SET stock_actual = COALESCE(stock_actual,0) + ?
                WHERE id_producto = ?
                """,
                cantidad,
                idProducto
        );
    }

    /* =========================================================
       DETALLES
       ========================================================= */

    private void insertarDetalleCompra(
            Integer idCompra,
            ItemOperacion item) {

        BigDecimal subtotal = dinero(
                item.cantidad().multiply(item.precioUnitario())
        );

        jdbcTemplate.update("""
                INSERT INTO dbo.detalle_compras (
                    id_compra,
                    id_insumo,
                    cantidad,
                    precio_unitario,
                    descuento,
                    subtotal
                )
                VALUES (?, ?, ?, ?, 0, ?)
                """,
                idCompra,
                item.idArticulo(),
                item.cantidad(),
                item.precioUnitario(),
                subtotal
        );
    }


    private void insertarDetalleVenta(
            Integer idVenta,
            ItemOperacion item) {

        BigDecimal subtotal = dinero(
                item.cantidad().multiply(item.precioUnitario())
        );

        /*
         * id_producto queda NULL.
         * La venta apunta al mismo id_insumo que utiliza Inventario.
         */
        jdbcTemplate.update("""
                INSERT INTO dbo.detalle_ventas (
                    id_venta,
                    id_producto,
                    id_insumo,
                    cantidad,
                    precio_unitario,
                    descuento,
                    subtotal
                )
                VALUES (?, NULL, ?, ?, ?, 0, ?)
                """,
                idVenta,
                item.idArticulo(),
                item.cantidad(),
                item.precioUnitario(),
                subtotal
        );
    }

    /* =========================================================
       PAGOS / FINANZAS
       ========================================================= */

    private void registrarPagoCompra(
            Integer idCompra,
            BigDecimal total,
            String metodoPago,
            String factura,
            Integer idEmpleado) {

        jdbcTemplate.update("""
                INSERT INTO dbo.pagos (
                    fecha_pago,
                    monto,
                    codigo_moneda,
                    referencia,
                    tipo_pago,
                    id_compra,
                    id_empleado,
                    metodo_pago,
                    estado,
                    observaciones
                )
                VALUES (
                    CONVERT(date,GETDATE()),
                    ?,
                    N'NIO',
                    ?,
                    N'COMPRA_PRODUCTO',
                    ?,
                    ?,
                    ?,
                    N'APLICADO',
                    N'Compra registrada desde Operaciones comerciales'
                )
                """,
                total,
                factura != null ? factura : "COMP-" + idCompra,
                idCompra,
                idEmpleado,
                metodoPago
        );
    }


    private void registrarPagoVenta(
            Integer idVenta,
            BigDecimal total,
            String metodoPago,
            String factura,
            Integer idEmpleado) {

        jdbcTemplate.update("""
                INSERT INTO dbo.pagos (
                    fecha_pago,
                    monto,
                    codigo_moneda,
                    referencia,
                    tipo_pago,
                    id_venta,
                    id_empleado,
                    metodo_pago,
                    estado,
                    observaciones
                )
                VALUES (
                    CONVERT(date,GETDATE()),
                    ?,
                    N'NIO',
                    ?,
                    N'VENTA_PRODUCTO',
                    ?,
                    ?,
                    ?,
                    N'APLICADO',
                    N'Venta registrada desde Operaciones comerciales'
                )
                """,
                total,
                factura != null ? factura : "VENT-" + idVenta,
                idVenta,
                idEmpleado,
                metodoPago
        );
    }


    private void actualizarPagoCompra(
            Integer idCompra,
            BigDecimal total,
            String metodoPago,
            String factura,
            Integer idEmpleado) {

        int actualizados = jdbcTemplate.update("""
                UPDATE dbo.pagos
                SET
                    monto = ?,
                    referencia = ?,
                    metodo_pago = ?,
                    id_empleado = ?,
                    estado = N'APLICADO'
                WHERE id_pago = (
                    SELECT TOP 1 id_pago
                    FROM dbo.pagos
                    WHERE id_compra = ?
                    ORDER BY id_pago DESC
                )
                """,
                total,
                factura != null ? factura : "COMP-" + idCompra,
                metodoPago,
                idEmpleado,
                idCompra
        );

        if (actualizados == 0) {
            registrarPagoCompra(
                    idCompra,
                    total,
                    metodoPago,
                    factura,
                    idEmpleado
            );
        }
    }


    private void actualizarPagoVenta(
            Integer idVenta,
            BigDecimal total,
            String metodoPago,
            String factura,
            Integer idEmpleado) {

        int actualizados = jdbcTemplate.update("""
                UPDATE dbo.pagos
                SET
                    monto = ?,
                    referencia = ?,
                    metodo_pago = ?,
                    id_empleado = ?,
                    estado = N'APLICADO'
                WHERE id_pago = (
                    SELECT TOP 1 id_pago
                    FROM dbo.pagos
                    WHERE id_venta = ?
                    ORDER BY id_pago DESC
                )
                """,
                total,
                factura != null ? factura : "VENT-" + idVenta,
                metodoPago,
                idEmpleado,
                idVenta
        );

        if (actualizados == 0) {
            registrarPagoVenta(
                    idVenta,
                    total,
                    metodoPago,
                    factura,
                    idEmpleado
            );
        }
    }


    /* =========================================================
       HELPERS SIMPLES
       ========================================================= */

    private Map<String, Object> respuestaOk(
            String mensaje,
            Integer id) {

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("ok", true);
        respuesta.put("id", id);
        respuesta.put("mensaje", mensaje);
        return respuesta;
    }


    private String texto(Object valor) {
        if (valor == null) return null;

        String resultado = String.valueOf(valor).trim();
        return resultado.isEmpty() ? null : resultado;
    }


    private String textoOpcional(Object valor) {
        return texto(valor);
    }


    private Integer entero(Object valor) {
        if (valor == null) return null;

        try {
            return Integer.valueOf(String.valueOf(valor));
        } catch (NumberFormatException error) {
            return null;
        }
    }


    private BigDecimal decimal(Object valor) {
        if (valor == null) return null;

        try {
            return dinero(
                    new BigDecimal(String.valueOf(valor))
            );
        } catch (NumberFormatException error) {
            return null;
        }
    }


    private BigDecimal decimalNoNegativo(Object valor) {

        BigDecimal resultado = decimal(valor);

        if (resultado == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (resultado.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los montos no pueden ser negativos."
            );
        }

        return resultado;
    }


    private BigDecimal numeroDecimal(Object valor) {

        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }

        return new BigDecimal(String.valueOf(valor));
    }


    private BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }


    private String metodoPago(Object valor) {

        String metodo = texto(valor);

        if (metodo == null) {
            return "Efectivo";
        }

        if (!List.of(
                "Efectivo",
                "Transferencia",
                "Tarjeta",
                "Cheque"
        ).contains(metodo)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Método de pago no válido."
            );
        }

        return metodo;
    }


    private record ItemOperacion(
            Integer idArticulo,
            BigDecimal cantidad,
            BigDecimal precioUnitario) {
    }
}
