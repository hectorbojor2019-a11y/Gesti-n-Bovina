
package com.gestionbovina.gestionbovina.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/aguinaldo")
@CrossOrigin(origins = "*")
public class AguinaldoController {

    private final JdbcTemplate db;

    public AguinaldoController(JdbcTemplate db) {
        this.db = db;
    }

    /* ========================================================
       FECHAS GENERALES DEL AGUINALDO
       ======================================================== */

    private LocalDate inicio(int anio) {
        return LocalDate.of(anio - 1, 12, 1);
    }

    private LocalDate fin(int anio) {
        return LocalDate.of(anio, 11, 30);
    }

    /* ========================================================
       VALIDACIÓN DEL AÑO
       ======================================================== */

    private void validar(int anio) {
        if (anio < 2000 || anio > 2100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Año inválido"
            );
        }
    }

    /* ========================================================
       CONSULTAR Y CALCULAR PLANILLA DE AGUINALDO
       ======================================================== */

    @GetMapping("/{anio}")
    public Map<String, Object> consultar(
            @PathVariable int anio) {

        validar(anio);

        LocalDate desde = inicio(anio);
        LocalDate hasta = fin(anio);

        /* ====================================================
           OBTENER EMPLEADOS DEL MÓDULO PERSONAL
           ==================================================== */

        List<Map<String, Object>> empleados =
            db.queryForList("""

                SELECT
                    e.id_empleado,
                    v.nombre_completo,
                    v.cargo,
                    e.fecha_ingreso,
                    e.salario_actual

                FROM dbo.empleados e

                INNER JOIN dbo.vw_personal_control v
                    ON v.id_empleado = e.id_empleado

                WHERE
                    e.Estado_actividad = 1
                    AND COALESCE(e.es_tecnico, 0) = 0
                    AND e.fecha_ingreso <= ?

                ORDER BY v.nombre_completo

                """, Date.valueOf(hasta));

        List<Map<String, Object>> filas =
            new ArrayList<>();

        BigDecimal total = BigDecimal.ZERO;

        /* ====================================================
           RECORRER EMPLEADOS
           ==================================================== */

        for (Map<String, Object> e : empleados) {

            int id = ((Number)
                e.get("id_empleado")).intValue();

            LocalDate ingreso = ((Date)
                e.get("fecha_ingreso")).toLocalDate();

            BigDecimal salario = new BigDecimal(
                e.get("salario_actual").toString()
            );

            /* =================================================
               CÁLCULO DEL AGUINALDO
               Se conserva la fórmula de PlanillaAguinaldo
               y el cierre anual del 30 de noviembre.
               ================================================= */

            PlanillaAguinaldo calculadora =
                new PlanillaAguinaldo(
                    salario,
                    ingreso,
                    desde,
                    hasta
                );

            /* =================================================
               CONSULTAR REGISTROS GUARDADOS
               ================================================= */

            List<Map<String, Object>> registros =
                db.queryForList("""

                    SELECT
                        id_provision,
                        monto_calculado,
                        pagado,
                        fecha_pago

                    FROM dbo.provisiones_laborales

                    WHERE
                        id_empleado = ?
                        AND tipo_provision = N'Aguinaldo'
                        AND periodo_inicio = ?
                        AND periodo_fin = ?

                    ORDER BY id_provision DESC

                    """,
                    id,
                    Date.valueOf(desde),
                    Date.valueOf(hasta)
                );

            Map<String, Object> fila =
                new LinkedHashMap<>(e);

            /* =================================================
               CORRECCIÓN DEL PERÍODO INDIVIDUAL
               ================================================= */

            // El período anual inicia el 1 de diciembre.
            // Si el empleado ingresó después, se utiliza
            // la fecha real de ingreso registrada en Personal.

            LocalDate inicioEmpleado =
                ingreso.isAfter(desde)
                    ? ingreso
                    : desde;

            // Se conserva el cierre anual seleccionado.
            LocalDate finEmpleado = hasta;

            // Campos que recibe aguinaldo.js.
            fila.put(
                "periodo_empleado_inicio",
                inicioEmpleado
            );

            fila.put(
                "periodo_empleado_fin",
                finEmpleado
            );

            /* =================================================
               DÍAS TRABAJADOS
               Se conserva el cálculo existente.
               ================================================= */

            fila.put(
                "dias_trabajados",
                calculadora.diasComputables()
            );

            /* =================================================
               AGUINALDO ESTIMADO
               Se conserva la fórmula existente.
               ================================================= */

            BigDecimal montoEstimado =
                calculadora.calcular();

            fila.put(
                "monto_estimado",
                montoEstimado
            );

            /* =================================================
               MONTO GUARDADO O CALCULADO
               ================================================= */

            boolean registrado = !registros.isEmpty();

            BigDecimal montoAguinaldo;

            if (registrado) {
                montoAguinaldo = new BigDecimal(
                    registros.get(0)
                        .get("monto_calculado")
                        .toString()
                );
            } else {
                montoAguinaldo = montoEstimado;
            }

            fila.put(
                "monto_aguinaldo",
                montoAguinaldo
            );

            /* =================================================
               DETECTAR DIFERENCIAS EN MONTOS GUARDADOS
               ================================================= */

            boolean montoGuardadoDiferente =
                registrado &&
                montoAguinaldo.compareTo(
                    montoEstimado
                ) != 0;

            fila.put(
                "monto_guardado_diferente",
                montoGuardadoDiferente
            );

            /* =================================================
               ESTADO DEL AGUINALDO
               ================================================= */

            fila.put(
                "registrado",
                registrado
            );

            fila.put(
                "pagado",
                registrado &&
                Boolean.TRUE.equals(
                    registros.get(0).get("pagado")
                )
            );

            fila.put(
                "fecha_pago",
                registrado
                    ? registros.get(0).get("fecha_pago")
                    : null
            );

            fila.put(
                "id_provision",
                registrado
                    ? registros.get(0).get("id_provision")
                    : null
            );

            /* =================================================
               SUMAR TOTAL
               ================================================= */

            total = total.add(montoAguinaldo);

            filas.add(fila);
        }

        /* ====================================================
           RESPUESTA PARA aguinaldo.js
           ==================================================== */

        Map<String, Object> respuesta =
            new LinkedHashMap<>();

        respuesta.put("anio", anio);

        respuesta.put(
            "periodo_inicio",
            desde
        );

        respuesta.put(
            "periodo_fin",
            hasta
        );

        respuesta.put(
            "fecha_corte",
            hasta
        );

        respuesta.put(
            "empleados",
            filas
        );

        respuesta.put(
            "total",
            total
        );

        return respuesta;
    }

    /* ========================================================
       GUARDAR PLANILLA DE AGUINALDO
       ======================================================== */

    @PostMapping("/{anio}/guardar")
    @Transactional
    public Map<String, Object> guardar(
            @PathVariable int anio) {

        validar(anio);

        Map<String, Object> consulta =
            consultar(anio);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> empleados =
            (List<Map<String, Object>>)
                consulta.get("empleados");

        for (Map<String, Object> empleado : empleados) {

            if (Boolean.TRUE.equals(
                    empleado.get("registrado"))) {
                continue;
            }

            db.update("""

                INSERT INTO dbo.provisiones_laborales
                    (
                        id_empleado,
                        tipo_provision,
                        periodo_inicio,
                        periodo_fin,
                        monto_calculado,
                        pagado,
                        fecha_pago,
                        observaciones
                    )

                VALUES (
                    ?,
                    N'Aguinaldo',
                    ?,
                    ?,
                    ?,
                    0,
                    NULL,
                    N'Cálculo inicial de aguinaldo'
                )

                """,
                empleado.get("id_empleado"),
                Date.valueOf(inicio(anio)),
                Date.valueOf(fin(anio)),
                empleado.get("monto_estimado")
            );
        }

        return consultar(anio);
    }

    /* ========================================================
       MARCAR AGUINALDO COMO PAGADO
       ======================================================== */

    @PostMapping("/{anio}/pagar/{idEmpleado}")
    @Transactional
    public Map<String, Object> pagar(
            @PathVariable int anio,
            @PathVariable int idEmpleado) {

        validar(anio);

        int actualizados = db.update("""

            UPDATE dbo.provisiones_laborales

            SET
                pagado = 1,
                fecha_pago = CAST(GETDATE() AS date)

            WHERE
                id_empleado = ?
                AND tipo_provision = N'Aguinaldo'
                AND periodo_inicio = ?
                AND periodo_fin = ?
                AND pagado = 0

            """,
            idEmpleado,
            Date.valueOf(inicio(anio)),
            Date.valueOf(fin(anio))
        );

        if (actualizados != 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No existe un aguinaldo pendiente único " +
                "para este empleado. Revise los registros."
            );
        }

        return consultar(anio);
    }
}
