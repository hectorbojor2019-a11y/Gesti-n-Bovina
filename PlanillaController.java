package com.gestionbovina.gestionbovina.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/planilla")
@CrossOrigin(origins = "*")
public class PlanillaController {

    protected JdbcTemplate jdbcTemplate;

    public PlanillaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /* =========================================================
       ESTADO
       ========================================================= */

    @GetMapping("/estado")
    public Map<String, Object> estado() {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        Integer activos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.empleados
                WHERE Estado_actividad = 1
                  AND COALESCE(es_tecnico,0) = 0
                """,
                Integer.class
        );

        respuesta.put("ok", true);
        respuesta.put("modulo", "PLANILLA");
        respuesta.put("empleados_activos", activos == null ? 0 : activos);

        return respuesta;
    }


    /* =========================================================
       PLANILLA ACTUAL
       ========================================================= */

    @GetMapping("/actual")
    public Map<String, Object> actual() {

        Integer idNomina = buscarNominaActual();

        if (idNomina == null) {

            Map<String, Object> respuesta = new LinkedHashMap<>();

            respuesta.put("existe", false);
            respuesta.put("periodo", "Mensual");
            respuesta.put("fecha_inicio", inicioMes());
            respuesta.put("fecha_fin", finMes());
            respuesta.put("estado", "Sin sincronizar");
            respuesta.put("detalles", List.of());

            Integer activos = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.empleados
                    WHERE Estado_actividad = 1
                      AND COALESCE(es_tecnico,0) = 0
                    """,
                    Integer.class
            );

            respuesta.put(
                    "empleados_disponibles",
                    activos == null ? 0 : activos
            );

            return respuesta;
        }

        return obtenerNomina(idNomina);
    }


    /* =========================================================
       SINCRONIZAR PERSONAL
       ========================================================= */

    @PostMapping("/sincronizar")
    @Transactional
    public Map<String, Object> sincronizar() {

        Integer idNomina = buscarNominaActual();

        if (idNomina == null) {
            idNomina = crearNominaPendiente();
        }

        String estado = estadoNomina(idNomina);

        if (!"Pendiente".equalsIgnoreCase(estado)) {

            Map<String, Object> respuesta = obtenerNomina(idNomina);

            respuesta.put(
                    "mensaje",
                    "La planilla del período ya está " +
                    estado.toLowerCase() +
                    " y no puede sincronizarse."
            );

            return respuesta;
        }

        List<Map<String, Object>> empleados =
                jdbcTemplate.queryForList("""
                        SELECT
                            id_empleado,
                            salario_actual
                        FROM dbo.empleados
                        WHERE Estado_actividad = 1
                          AND COALESCE(es_tecnico,0) = 0
                        ORDER BY id_empleado
                        """);

        for (Map<String, Object> empleado : empleados) {

            Integer idEmpleado =
                    numeroEntero(empleado.get("id_empleado"));

            BigDecimal salario =
                    numeroDecimal(empleado.get("salario_actual"));

            List<Map<String, Object>> detalleExistente =
                    jdbcTemplate.queryForList("""
                            SELECT
                                id_detalle,
                                estado_detalle,
                                dias_trabajados,
                                horas_extra,
                                monto_horas_extra,
                                bonificaciones,
                                otras_deducciones,
                                observaciones
                            FROM dbo.detalle_nominas
                            WHERE id_nomina = ?
                              AND id_empleado = ?
                            """,
                            idNomina,
                            idEmpleado
                    );

            if (detalleExistente.isEmpty()) {

                Calculo calculo = calcular(
                        salario,
                        decimal("30"),
                        decimal("0"),
                        decimal("0"),
                        decimal("0"),
                        decimal("0")
                );

                insertarDetalle(
                        idNomina,
                        idEmpleado,
                        salario,
                        decimal("30"),
                        decimal("0"),
                        decimal("0"),
                        decimal("0"),
                        decimal("0"),
                        null,
                        calculo
                );

                continue;
            }

            Map<String, Object> detalle =
                    detalleExistente.get(0);

            String estadoDetalle =
                    texto(detalle.get("estado_detalle"));

            /*
             * Si fue anulado manualmente, Sincronizar no lo reincorpora
             * automáticamente. Para eso existe "Agregar a planilla".
             */
            if ("ANULADO".equalsIgnoreCase(estadoDetalle)) {
                continue;
            }

            BigDecimal dias =
                    numeroDecimal(detalle.get("dias_trabajados"));

            BigDecimal horasExtra =
                    numeroDecimal(detalle.get("horas_extra"));

            BigDecimal montoHorasExtra =
                    numeroDecimal(detalle.get("monto_horas_extra"));

            BigDecimal bonificaciones =
                    numeroDecimal(detalle.get("bonificaciones"));

            BigDecimal otrasDeducciones =
                    numeroDecimal(detalle.get("otras_deducciones"));

            Calculo calculo = calcular(
                    salario,
                    dias,
                    horasExtra,
                    montoHorasExtra,
                    bonificaciones,
                    otrasDeducciones
            );

            actualizarDetalleCalculado(
                    numeroEntero(detalle.get("id_detalle")),
                    salario,
                    dias,
                    horasExtra,
                    montoHorasExtra,
                    bonificaciones,
                    otrasDeducciones,
                    texto(detalle.get("observaciones")),
                    calculo
            );
        }

        /*
         * Si Personal dio de baja a alguien, se excluye de la planilla
         * pendiente sin borrar el registro.
         */
        jdbcTemplate.update("""
                UPDATE dn
                SET
                    dn.estado_detalle = N'ANULADO',
                    dn.motivo_anulacion =
                        N'Sincronización: empleado inactivo en Personal',
                    dn.fecha_anulacion = SYSDATETIME(),
                    dn.fecha_actualizacion = SYSDATETIME()
                FROM dbo.detalle_nominas dn
                INNER JOIN dbo.empleados e
                    ON e.id_empleado = dn.id_empleado
                WHERE dn.id_nomina = ?
                  AND dn.estado_detalle = N'ACTIVO'
                  AND (
                        e.Estado_actividad = 0
                        OR COALESCE(e.es_tecnico,0) = 1
                      )
                """,
                idNomina
        );

        recalcularTotalesNomina(idNomina);

        Map<String, Object> respuesta =
                obtenerNomina(idNomina);

        respuesta.put(
                "mensaje",
                "Personal sincronizado correctamente con la planilla."
        );

        return respuesta;
    }


    /* =========================================================
       DISPONIBLES PARA REINCORPORAR
       ========================================================= */

    @GetMapping("/disponibles")
    public List<Map<String, Object>> disponibles() {

        Integer idNomina = buscarNominaActual();

        if (idNomina == null) {

            return jdbcTemplate.queryForList("""
                    SELECT
                        v.id_empleado,
                        v.codigo_empleado,
                        v.nombre_completo,
                        v.cargo,
                        v.area,
                        v.salario_actual
                    FROM dbo.vw_personal_control v
                    WHERE v.Estado_actividad = 1
                      AND COALESCE(v.es_tecnico,0) = 0
                    ORDER BY v.nombre_completo
                    """);
        }

        return jdbcTemplate.queryForList("""
                SELECT
                    v.id_empleado,
                    v.codigo_empleado,
                    v.nombre_completo,
                    v.cargo,
                    v.area,
                    v.salario_actual
                FROM dbo.vw_personal_control v
                LEFT JOIN dbo.detalle_nominas dn
                    ON dn.id_nomina = ?
                   AND dn.id_empleado = v.id_empleado
                WHERE v.Estado_actividad = 1
                  AND COALESCE(v.es_tecnico,0) = 0
                  AND (
                        dn.id_detalle IS NULL
                        OR dn.estado_detalle = N'ANULADO'
                      )
                ORDER BY v.nombre_completo
                """,
                idNomina
        );
    }


    /* =========================================================
       AGREGAR / REINCORPORAR
       ========================================================= */

    @PostMapping("/agregar/{idEmpleado}")
    @Transactional
    public Map<String, Object> agregar(
            @PathVariable Integer idEmpleado) {

        Integer idNomina = buscarNominaActual();

        if (idNomina == null) {
            idNomina = crearNominaPendiente();
        }

        validarNominaPendiente(idNomina);

        List<Map<String, Object>> empleados =
                jdbcTemplate.queryForList("""
                        SELECT
                            id_empleado,
                            salario_actual
                        FROM dbo.empleados
                        WHERE id_empleado = ?
                          AND Estado_actividad = 1
                          AND COALESCE(es_tecnico,0) = 0
                        """,
                        idEmpleado
                );

        if (empleados.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado no está activo en Personal."
            );
        }

        BigDecimal salario =
                numeroDecimal(
                        empleados.get(0).get("salario_actual")
                );

        List<Map<String, Object>> existentes =
                jdbcTemplate.queryForList("""
                        SELECT id_detalle
                        FROM dbo.detalle_nominas
                        WHERE id_nomina = ?
                          AND id_empleado = ?
                        """,
                        idNomina,
                        idEmpleado
                );

        Calculo calculo = calcular(
                salario,
                decimal("30"),
                decimal("0"),
                decimal("0"),
                decimal("0"),
                decimal("0")
        );

        if (existentes.isEmpty()) {

            insertarDetalle(
                    idNomina,
                    idEmpleado,
                    salario,
                    decimal("30"),
                    decimal("0"),
                    decimal("0"),
                    decimal("0"),
                    decimal("0"),
                    null,
                    calculo
            );

        } else {

            Integer idDetalle =
                    numeroEntero(
                            existentes.get(0).get("id_detalle")
                    );

            jdbcTemplate.update("""
                    UPDATE dbo.detalle_nominas
                    SET
                        salario_base = ?,
                        dias_trabajados = 30,
                        horas_extra = 0,
                        monto_horas_extra = 0,
                        bonificaciones = 0,
                        total_devengado = ?,
                        inss_laboral = ?,
                        ir_salario = ?,
                        otras_deducciones = 0,
                        total_deducciones = ?,
                        salario_neto = ?,
                        inss_patronal = ?,
                        inatec = ?,
                        estado_detalle = N'ACTIVO',
                        motivo_anulacion = NULL,
                        fecha_anulacion = NULL,
                        fecha_actualizacion = SYSDATETIME()
                    WHERE id_detalle = ?
                    """,
                    salario,
                    calculo.totalDevengado(),
                    calculo.inssLaboral(),
                    calculo.irMensual(),
                    calculo.totalDeducciones(),
                    calculo.salarioNeto(),
                    calculo.inssPatronal(),
                    calculo.inatec(),
                    idDetalle
            );
        }

        recalcularTotalesNomina(idNomina);

        Map<String, Object> respuesta =
                obtenerNomina(idNomina);

        respuesta.put(
                "mensaje",
                "Empleado agregado a la planilla correctamente."
        );

        return respuesta;
    }


    /* =========================================================
       MODIFICAR DETALLE
       ========================================================= */

    @PutMapping("/detalles/{idDetalle}")
    @Transactional
    public Map<String, Object> modificarDetalle(
            @PathVariable Integer idDetalle,
            @RequestBody Map<String, Object> datos) {

        Map<String, Object> detalle =
                detalleEditable(idDetalle);

        Integer idNomina =
                numeroEntero(detalle.get("id_nomina"));

        BigDecimal salarioBase =
                numeroDecimal(detalle.get("salario_base"));

        BigDecimal dias =
                decimalNoNegativo(datos.get("diasTrabajados"));

        BigDecimal horasExtra =
                decimalNoNegativo(datos.get("horasExtra"));

        BigDecimal montoHorasExtra =
                decimalNoNegativo(datos.get("montoHorasExtra"));

        BigDecimal bonificaciones =
                decimalNoNegativo(datos.get("bonificaciones"));

        BigDecimal otrasDeducciones =
                decimalNoNegativo(datos.get("otrasDeducciones"));

        String observaciones =
                texto(datos.get("observaciones"));

        if (dias.compareTo(decimal("30")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los días trabajados no pueden superar 30."
            );
        }

        Calculo calculo = calcular(
                salarioBase,
                dias,
                horasExtra,
                montoHorasExtra,
                bonificaciones,
                otrasDeducciones
        );

        actualizarDetalleCalculado(
                idDetalle,
                salarioBase,
                dias,
                horasExtra,
                montoHorasExtra,
                bonificaciones,
                otrasDeducciones,
                observaciones,
                calculo
        );

        recalcularTotalesNomina(idNomina);

        Map<String, Object> respuesta =
                obtenerNomina(idNomina);

        respuesta.put(
                "mensaje",
                "Detalle de planilla modificado correctamente."
        );

        return respuesta;
    }


    /* =========================================================
       ANULAR / EXCLUIR DETALLE
       ========================================================= */

    @PostMapping("/detalles/{idDetalle}/anular")
    @Transactional
    public Map<String, Object> anularDetalle(
            @PathVariable Integer idDetalle,
            @RequestBody Map<String, Object> datos) {

        Map<String, Object> detalle =
                detalleEditable(idDetalle);

        String motivo =
                texto(datos.get("motivo"));

        if (motivo == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Indica el motivo de la exclusión."
            );
        }

        Integer idNomina =
                numeroEntero(detalle.get("id_nomina"));

        jdbcTemplate.update("""
                UPDATE dbo.detalle_nominas
                SET
                    estado_detalle = N'ANULADO',
                    motivo_anulacion = ?,
                    fecha_anulacion = SYSDATETIME(),
                    fecha_actualizacion = SYSDATETIME()
                WHERE id_detalle = ?
                """,
                motivo,
                idDetalle
        );

        recalcularTotalesNomina(idNomina);

        Map<String, Object> respuesta =
                obtenerNomina(idNomina);

        respuesta.put(
                "mensaje",
                "Empleado excluido de esta planilla. Su registro en Personal no fue eliminado."
        );

        return respuesta;
    }


    /* =========================================================
       PROCESAR PLANILLA
       ========================================================= */

    @PostMapping("/procesar")
    @Transactional
    public Map<String, Object> procesar() {

        Integer idNomina = buscarNominaActual();

        if (idNomina == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Primero sincroniza el personal."
            );
        }

        validarNominaPendiente(idNomina);

        Integer activos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.detalle_nominas
                WHERE id_nomina = ?
                  AND estado_detalle = N'ACTIVO'
                """,
                Integer.class,
                idNomina
        );

        if (activos == null || activos == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No hay empleados activos en la planilla."
            );
        }

        recalcularTotalesNomina(idNomina);

        jdbcTemplate.update("""
                UPDATE dbo.nominas
                SET
                    estado = N'Procesada',
                    fecha_actualizacion = SYSDATETIME()
                WHERE id_nomina = ?
                """,
                idNomina
        );

        Map<String, Object> respuesta =
                obtenerNomina(idNomina);

        respuesta.put(
                "mensaje",
                "Planilla procesada correctamente."
        );

        return respuesta;
    }


    /* =========================================================
       MARCAR PAGADA
       ========================================================= */

    @PostMapping("/{idNomina}/pagar")
    @Transactional
    public Map<String, Object> pagar(
            @PathVariable Integer idNomina,
            @RequestBody(required = false) Map<String, Object> datos) {

        List<Map<String, Object>> nominas =
                jdbcTemplate.queryForList("""
                        SELECT
                            estado,
                            total_pagar
                        FROM dbo.nominas
                        WHERE id_nomina = ?
                        """,
                        idNomina
                );

        if (nominas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Planilla no encontrada."
            );
        }

        String estado =
                texto(nominas.get(0).get("estado"));

        if ("Pagada".equalsIgnoreCase(estado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La planilla ya fue pagada."
            );
        }

        if (!"Procesada".equalsIgnoreCase(estado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Primero debes procesar la planilla."
            );
        }

        String metodoPago =
                datos == null
                        ? "Transferencia"
                        : texto(datos.get("metodoPago"));

        if (metodoPago == null) {
            metodoPago = "Transferencia";
        }

        BigDecimal total =
                numeroDecimal(
                        nominas.get(0).get("total_pagar")
                );

        jdbcTemplate.update("""
                UPDATE dbo.nominas
                SET
                    estado = N'Pagada',
                    fecha_pago = CONVERT(date,GETDATE()),
                    fecha_actualizacion = SYSDATETIME()
                WHERE id_nomina = ?
                """,
                idNomina
        );

        Integer pagosExistentes =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.pagos
                        WHERE id_nomina = ?
                          AND UPPER(COALESCE(estado,N'')) <> N'ANULADO'
                        """,
                        Integer.class,
                        idNomina
                );

        if (pagosExistentes == null || pagosExistentes == 0) {

            jdbcTemplate.update("""
                    INSERT INTO dbo.pagos (
                        fecha_pago,
                        monto,
                        codigo_moneda,
                        referencia,
                        tipo_pago,
                        id_nomina,
                        id_empleado,
                        observaciones,
                        metodo_pago,
                        estado
                    )
                    VALUES (
                        CONVERT(date,GETDATE()),
                        ?,
                        N'NIO',
                        ?,
                        N'NOMINA',
                        ?,
                        NULL,
                        N'Pago de planilla registrado desde Finanzas',
                        ?,
                        N'APLICADO'
                    )
                    """,
                    total,
                    "NOM-" + idNomina,
                    idNomina,
                    metodoPago
            );
        }

        Map<String, Object> respuesta =
                obtenerNomina(idNomina);

        respuesta.put(
                "mensaje",
                "Planilla marcada como pagada y registrada como egreso financiero."
        );

        return respuesta;
    }


    /* =========================================================
       HISTORIAL
       ========================================================= */

    @GetMapping("/historial")
    public List<Map<String, Object>> historial() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_nomina,
                    fecha_inicio,
                    fecha_fin,
                    fecha_pago,
                    periodo,
                    estado,
                    COALESCE(total_devengado,0) AS total_devengado,
                    COALESCE(total_deducciones,0) AS total_deducciones,
                    COALESCE(total_pagar,0) AS total_pagar,
                    COALESCE(total_inss_patronal,0) AS total_inss_patronal,
                    COALESCE(total_inatec,0) AS total_inatec,
                    observaciones
                FROM dbo.nominas
                ORDER BY fecha_inicio DESC, id_nomina DESC
                """);
    }


    @GetMapping("/{idNomina}")
    public Map<String, Object> detalleNomina(
            @PathVariable Integer idNomina) {

        return obtenerNomina(idNomina);
    }


    /* =========================================================
       HELPERS - NÓMINA
       ========================================================= */

    private Integer buscarNominaActual() {

        List<Integer> ids =
                jdbcTemplate.queryForList("""
                        SELECT TOP 1 id_nomina
                        FROM dbo.nominas
                        WHERE fecha_inicio = ?
                          AND fecha_fin = ?
                          AND periodo = N'Mensual'
                        ORDER BY id_nomina DESC
                        """,
                        Integer.class,
                        inicioMes(),
                        finMes()
                );

        return ids.isEmpty()
                ? null
                : ids.get(0);
    }


    private Integer crearNominaPendiente() {

        return jdbcTemplate.queryForObject("""
                INSERT INTO dbo.nominas (
                    fecha_inicio,
                    fecha_fin,
                    fecha_pago,
                    periodo,
                    estado,
                    total_devengado,
                    total_deducciones,
                    total_pagar,
                    total_inss_patronal,
                    total_inatec,
                    observaciones,
                    fecha_registro,
                    fecha_actualizacion
                )
                OUTPUT INSERTED.id_nomina
                VALUES (
                    ?, ?, NULL,
                    N'Mensual',
                    N'Pendiente',
                    0,0,0,0,0,
                    N'Planilla mensual sincronizada con Personal',
                    SYSDATETIME(),
                    SYSDATETIME()
                )
                """,
                Integer.class,
                inicioMes(),
                finMes()
        );
    }


    private String estadoNomina(Integer idNomina) {

        List<String> estados =
                jdbcTemplate.queryForList("""
                        SELECT estado
                        FROM dbo.nominas
                        WHERE id_nomina = ?
                        """,
                        String.class,
                        idNomina
                );

        if (estados.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Planilla no encontrada."
            );
        }

        return estados.get(0);
    }


    private void validarNominaPendiente(Integer idNomina) {

        String estado = estadoNomina(idNomina);

        if (!"Pendiente".equalsIgnoreCase(estado)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Solo se puede modificar una planilla pendiente."
            );
        }
    }


    private Map<String, Object> detalleEditable(Integer idDetalle) {

        List<Map<String, Object>> filas =
                jdbcTemplate.queryForList("""
                        SELECT
                            dn.id_detalle,
                            dn.id_nomina,
                            dn.salario_base,
                            dn.estado_detalle,
                            n.estado AS estado_nomina
                        FROM dbo.detalle_nominas dn
                        INNER JOIN dbo.nominas n
                            ON n.id_nomina = dn.id_nomina
                        WHERE dn.id_detalle = ?
                        """,
                        idDetalle
                );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Detalle de planilla no encontrado."
            );
        }

        Map<String, Object> fila = filas.get(0);

        if (!"Pendiente".equalsIgnoreCase(
                texto(fila.get("estado_nomina"))
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La planilla ya no puede modificarse."
            );
        }

        if (!"ACTIVO".equalsIgnoreCase(
                texto(fila.get("estado_detalle"))
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado está excluido de esta planilla."
            );
        }

        return fila;
    }


    private Map<String, Object> obtenerNomina(Integer idNomina) {

        List<Map<String, Object>> cabeceras =
                jdbcTemplate.queryForList("""
                        SELECT
                            id_nomina,
                            fecha_inicio,
                            fecha_fin,
                            fecha_pago,
                            periodo,
                            estado,
                            COALESCE(total_devengado,0) AS total_devengado,
                            COALESCE(total_deducciones,0) AS total_deducciones,
                            COALESCE(total_pagar,0) AS total_pagar,
                            COALESCE(total_inss_patronal,0) AS total_inss_patronal,
                            COALESCE(total_inatec,0) AS total_inatec,
                            observaciones
                        FROM dbo.nominas
                        WHERE id_nomina = ?
                        """,
                        idNomina
                );

        if (cabeceras.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Planilla no encontrada."
            );
        }

        List<Map<String, Object>> detalles =
                jdbcTemplate.queryForList("""
                        SELECT
                            id_detalle,
                            id_empleado,
                            codigo_empleado,
                            nombre_completo,
                            cargo,
                            area,
                            salario_base,
                            dias_trabajados,
                            horas_extra,
                            monto_horas_extra,
                            bonificaciones,
                            total_devengado,
                            inss_laboral,
                            ir_salario,
                            otras_deducciones,
                            total_deducciones,
                            salario_neto,
                            inss_patronal,
                            inatec,
                            observaciones,
                            estado_detalle,
                            motivo_anulacion,
                            fecha_anulacion
                        FROM dbo.vw_planilla_detalle
                        WHERE id_nomina = ?
                        ORDER BY
                            CASE
                                WHEN estado_detalle = N'ACTIVO'
                                THEN 0 ELSE 1
                            END,
                            nombre_completo
                        """,
                        idNomina
                );

        List<Map<String, Object>> detallesExtendidos =
                new ArrayList<>();

        for (Map<String, Object> detalle : detalles) {

            Map<String, Object> fila =
                    new LinkedHashMap<>(detalle);

            BigDecimal totalDevengado =
                    numeroDecimal(detalle.get("total_devengado"));

            BigDecimal inss =
                    numeroDecimal(detalle.get("inss_laboral"));

            BigDecimal gravableAnual =
                    dinero(
                            totalDevengado
                                    .subtract(inss)
                                    .max(BigDecimal.ZERO)
                                    .multiply(decimal("12"))
                    );

            BigDecimal irMensual =
                    numeroDecimal(detalle.get("ir_salario"));

            fila.put(
                    "salario_gravable_anual",
                    gravableAnual
            );

            fila.put(
                    "ir_anual",
                    dinero(
                            irMensual.multiply(decimal("12"))
                    )
            );

            detallesExtendidos.add(fila);
        }

        Map<String, Object> respuesta =
                new LinkedHashMap<>(cabeceras.get(0));

        respuesta.put("existe", true);
        respuesta.put("detalles", detallesExtendidos);

        Integer activos =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.detalle_nominas
                        WHERE id_nomina = ?
                          AND estado_detalle = N'ACTIVO'
                        """,
                        Integer.class,
                        idNomina
                );

        respuesta.put(
                "empleados_activos",
                activos == null ? 0 : activos
        );

        return respuesta;
    }


    private void insertarDetalle(
            Integer idNomina,
            Integer idEmpleado,
            BigDecimal salarioBase,
            BigDecimal dias,
            BigDecimal horasExtra,
            BigDecimal montoHorasExtra,
            BigDecimal bonificaciones,
            BigDecimal otrasDeducciones,
            String observaciones,
            Calculo calculo) {

        jdbcTemplate.update("""
                INSERT INTO dbo.detalle_nominas (
                    id_nomina,
                    id_empleado,
                    salario_base,
                    dias_trabajados,
                    horas_extra,
                    monto_horas_extra,
                    bonificaciones,
                    total_devengado,
                    inss_laboral,
                    ir_salario,
                    otras_deducciones,
                    total_deducciones,
                    salario_neto,
                    inss_patronal,
                    inatec,
                    observaciones,
                    estado_detalle,
                    fecha_actualizacion
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    N'ACTIVO',
                    SYSDATETIME()
                )
                """,
                idNomina,
                idEmpleado,
                salarioBase,
                dias,
                horasExtra,
                montoHorasExtra,
                bonificaciones,
                calculo.totalDevengado(),
                calculo.inssLaboral(),
                calculo.irMensual(),
                otrasDeducciones,
                calculo.totalDeducciones(),
                calculo.salarioNeto(),
                calculo.inssPatronal(),
                calculo.inatec(),
                observaciones
        );
    }


    private void actualizarDetalleCalculado(
            Integer idDetalle,
            BigDecimal salarioBase,
            BigDecimal dias,
            BigDecimal horasExtra,
            BigDecimal montoHorasExtra,
            BigDecimal bonificaciones,
            BigDecimal otrasDeducciones,
            String observaciones,
            Calculo calculo) {

        jdbcTemplate.update("""
                UPDATE dbo.detalle_nominas
                SET
                    salario_base = ?,
                    dias_trabajados = ?,
                    horas_extra = ?,
                    monto_horas_extra = ?,
                    bonificaciones = ?,
                    total_devengado = ?,
                    inss_laboral = ?,
                    ir_salario = ?,
                    otras_deducciones = ?,
                    total_deducciones = ?,
                    salario_neto = ?,
                    inss_patronal = ?,
                    inatec = ?,
                    observaciones = ?,
                    fecha_actualizacion = SYSDATETIME()
                WHERE id_detalle = ?
                """,
                salarioBase,
                dias,
                horasExtra,
                montoHorasExtra,
                bonificaciones,
                calculo.totalDevengado(),
                calculo.inssLaboral(),
                calculo.irMensual(),
                otrasDeducciones,
                calculo.totalDeducciones(),
                calculo.salarioNeto(),
                calculo.inssPatronal(),
                calculo.inatec(),
                observaciones,
                idDetalle
        );
    }


    private void recalcularTotalesNomina(Integer idNomina) {

        jdbcTemplate.update("""
                UPDATE n
                SET
                    n.total_devengado = x.total_devengado,
                    n.total_deducciones = x.total_deducciones,
                    n.total_pagar = x.total_pagar,
                    n.total_inss_patronal = x.total_inss_patronal,
                    n.total_inatec = x.total_inatec,
                    n.fecha_actualizacion = SYSDATETIME()
                FROM dbo.nominas n
                CROSS APPLY (
                    SELECT
                        CAST(COALESCE(SUM(
                            CASE
                                WHEN estado_detalle = N'ACTIVO'
                                THEN total_devengado
                                ELSE 0
                            END
                        ),0) AS DECIMAL(12,2)) AS total_devengado,

                        CAST(COALESCE(SUM(
                            CASE
                                WHEN estado_detalle = N'ACTIVO'
                                THEN total_deducciones
                                ELSE 0
                            END
                        ),0) AS DECIMAL(12,2)) AS total_deducciones,

                        CAST(COALESCE(SUM(
                            CASE
                                WHEN estado_detalle = N'ACTIVO'
                                THEN salario_neto
                                ELSE 0
                            END
                        ),0) AS DECIMAL(12,2)) AS total_pagar,

                        CAST(COALESCE(SUM(
                            CASE
                                WHEN estado_detalle = N'ACTIVO'
                                THEN inss_patronal
                                ELSE 0
                            END
                        ),0) AS DECIMAL(12,2)) AS total_inss_patronal,

                        CAST(COALESCE(SUM(
                            CASE
                                WHEN estado_detalle = N'ACTIVO'
                                THEN inatec
                                ELSE 0
                            END
                        ),0) AS DECIMAL(12,2)) AS total_inatec

                    FROM dbo.detalle_nominas
                    WHERE id_nomina = n.id_nomina
                ) x
                WHERE n.id_nomina = ?
                """,
                idNomina
        );
    }


    /* =========================================================
       CÁLCULOS
       ========================================================= */

    private Calculo calcular(
            BigDecimal salarioBase,
            BigDecimal diasTrabajados,
            BigDecimal horasExtra,
            BigDecimal montoHorasExtra,
            BigDecimal bonificaciones,
            BigDecimal otrasDeducciones) {

        BigDecimal dias =
                diasTrabajados == null
                        ? decimal("30")
                        : diasTrabajados;

        BigDecimal montoExtra =
                valorCero(montoHorasExtra);

        BigDecimal bonos =
                valorCero(bonificaciones);

        BigDecimal otras =
                valorCero(otrasDeducciones);

        BigDecimal salarioPorDias =
                salarioBase
                        .multiply(dias)
                        .divide(
                                decimal("30"),
                                8,
                                RoundingMode.HALF_UP
                        );

        BigDecimal totalDevengado =
                dinero(
                        salarioPorDias
                                .add(montoExtra)
                                .add(bonos)
                );

        BigDecimal tasaInssLaboral =
                configuracionDecimal(
                        "PLANILLA_INSS_LABORAL",
                        "0.07"
                );

        BigDecimal tasaInssPatronal =
                configuracionDecimal(
                        "PLANILLA_INSS_PATRONAL",
                        "0.215"
                );

        BigDecimal tasaInatec =
                configuracionDecimal(
                        "PLANILLA_INATEC",
                        "0.02"
                );

        BigDecimal inssLaboral =
                dinero(
                        totalDevengado
                                .multiply(tasaInssLaboral)
                );

        BigDecimal gravableAnual =
                dinero(
                        totalDevengado
                                .subtract(inssLaboral)
                                .max(BigDecimal.ZERO)
                                .multiply(decimal("12"))
                );

        BigDecimal irAnual =
                calcularIRAnual(gravableAnual);

        BigDecimal irMensual =
                dinero(
                        irAnual
                                .divide(
                                        decimal("12"),
                                        8,
                                        RoundingMode.HALF_UP
                                )
                );

        BigDecimal totalDeducciones =
                dinero(
                        inssLaboral
                                .add(irMensual)
                                .add(otras)
                );

        BigDecimal salarioNeto =
                dinero(
                        totalDevengado
                                .subtract(totalDeducciones)
                                .max(BigDecimal.ZERO)
                );

        BigDecimal inssPatronal =
                dinero(
                        totalDevengado
                                .multiply(tasaInssPatronal)
                );

        BigDecimal inatec =
                dinero(
                        totalDevengado
                                .multiply(tasaInatec)
                );

        return new Calculo(
                totalDevengado,
                inssLaboral,
                irMensual,
                totalDeducciones,
                salarioNeto,
                inssPatronal,
                inatec
        );
    }


    private BigDecimal calcularIRAnual(
            BigDecimal ingreso) {

        if (ingreso.compareTo(decimal("100000")) <= 0) {
            return decimal("0");
        }

        if (ingreso.compareTo(decimal("200000")) <= 0) {
            return dinero(
                    ingreso
                            .subtract(decimal("100000"))
                            .multiply(decimal("0.15"))
            );
        }

        if (ingreso.compareTo(decimal("350000")) <= 0) {
            return dinero(
                    decimal("15000")
                            .add(
                                    ingreso
                                            .subtract(decimal("200000"))
                                            .multiply(decimal("0.20"))
                            )
            );
        }

        if (ingreso.compareTo(decimal("500000")) <= 0) {
            return dinero(
                    decimal("45000")
                            .add(
                                    ingreso
                                            .subtract(decimal("350000"))
                                            .multiply(decimal("0.25"))
                            )
            );
        }

        return dinero(
                decimal("82500")
                        .add(
                                ingreso
                                        .subtract(decimal("500000"))
                                        .multiply(decimal("0.30"))
                        )
        );
    }


    private BigDecimal configuracionDecimal(
            String clave,
            String defecto) {

        List<String> valores =
                jdbcTemplate.queryForList("""
                        SELECT valor
                        FROM dbo.configuracion_sistema
                        WHERE clave = ?
                        """,
                        String.class,
                        clave
                );

        if (valores.isEmpty()) {
            return decimal(defecto);
        }

        try {
            return decimal(valores.get(0));
        } catch (Exception error) {
            return decimal(defecto);
        }
    }


    /* =========================================================
       HELPERS SIMPLES
       ========================================================= */

    private LocalDate inicioMes() {
        return LocalDate
                .now()
                .withDayOfMonth(1);
    }


    private LocalDate finMes() {
        return LocalDate
                .now()
                .with(
                        TemporalAdjusters.lastDayOfMonth()
                );
    }


    private BigDecimal decimalNoNegativo(Object valor) {

        BigDecimal resultado =
                valor == null
                        ? decimal("0")
                        : numeroDecimal(valor);

        if (resultado.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los valores de planilla no pueden ser negativos."
            );
        }

        return dinero(resultado);
    }


    private BigDecimal numeroDecimal(Object valor) {

        if (valor == null) {
            return decimal("0");
        }

        if (valor instanceof BigDecimal decimal) {
            return decimal;
        }

        return new BigDecimal(
                String.valueOf(valor)
        );
    }


    private Integer numeroEntero(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof Number numero) {
            return numero.intValue();
        }

        return Integer.valueOf(
                String.valueOf(valor)
        );
    }


    private BigDecimal decimal(String valor) {
        return new BigDecimal(valor);
    }


    private BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }


    private BigDecimal valorCero(BigDecimal valor) {
        return valor == null
                ? decimal("0")
                : valor;
    }


    private String texto(Object valor) {

        if (valor == null) {
            return null;
        }

        String resultado =
                String.valueOf(valor).trim();

        return resultado.isEmpty()
                ? null
                : resultado;
    }


    private record Calculo(
            BigDecimal totalDevengado,
            BigDecimal inssLaboral,
            BigDecimal irMensual,
            BigDecimal totalDeducciones,
            BigDecimal salarioNeto,
            BigDecimal inssPatronal,
            BigDecimal inatec) {
    }
}
