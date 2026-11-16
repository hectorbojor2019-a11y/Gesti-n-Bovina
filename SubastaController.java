package com.gestionbovina.gestionbovina.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subastas")
@CrossOrigin(origins = "*")
public class SubastaController {

    private final JdbcTemplate jdbcTemplate;

    public SubastaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /* =========================================================
       1. ESTADO DEL MÓDULO
       ========================================================= */

    @GetMapping("/estado")
    public Map<String, Object> estado() {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.subastas",
                Integer.class
        );

        Integer programadas = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.subastas
                WHERE estado = N'Programada'
                """,
                Integer.class
        );

        Integer enCurso = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.subastas
                WHERE estado = N'En curso'
                """,
                Integer.class
        );

        Integer finalizadas = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.subastas
                WHERE estado = N'Finalizada'
                """,
                Integer.class
        );

        respuesta.put("ok", true);
        respuesta.put("modulo", "SUBASTAS");
        respuesta.put("total_subastas", numero(total));
        respuesta.put("programadas", numero(programadas));
        respuesta.put("en_curso", numero(enCurso));
        respuesta.put("finalizadas", numero(finalizadas));

        return respuesta;
    }


    /* =========================================================
       2. LISTAR SUBASTAS
       ========================================================= */

    @GetMapping
    public List<Map<String, Object>> listar() {

        return jdbcTemplate.queryForList(
                """
                SELECT *
                FROM dbo.vw_subastas_resumen
                ORDER BY
                    CASE estado
                        WHEN N'En curso' THEN 1
                        WHEN N'Programada' THEN 2
                        WHEN N'Borrador' THEN 3
                        WHEN N'Finalizada' THEN 4
                        ELSE 5
                    END,
                    fecha_programada DESC,
                    id_subasta DESC
                """
        );
    }


    /* =========================================================
       3. DETALLE DE UNA SUBASTA
       ========================================================= */

    @GetMapping("/{idSubasta}")
    public Object detalle(
            @PathVariable Integer idSubasta) {

        List<Map<String, Object>> filas =
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM dbo.vw_subastas_resumen
                        WHERE id_subasta = ?
                        """,
                        idSubasta
                );

        if (filas.isEmpty()) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return filas.get(0);
    }


    /* =========================================================
       4. BOVINOS DISPONIBLES / APTITUD
       ========================================================= */

    @GetMapping("/catalogos/bovinos")
    public List<Map<String, Object>> bovinos() {

        return jdbcTemplate.queryForList(
                """
                SELECT *
                FROM dbo.vw_ganado_aptitud_subasta
                ORDER BY
                    apto_subasta DESC,
                    activo DESC,
                    COALESCE(nombre_apodo,N''),
                    codigo_arete
                """
        );
    }


    /* =========================================================
       5. FICHA COMPLETA DE UN BOVINO PARA SUBASTA
       Incluye aptitud, fotos e historial veterinario.
       ========================================================= */

    @GetMapping("/bovinos/{idGanado}")
    public Object fichaBovino(
            @PathVariable Integer idGanado) {

        List<Map<String, Object>> bovinos =
                jdbcTemplate.queryForList(
                        """
                        SELECT *
                        FROM dbo.vw_ganado_aptitud_subasta
                        WHERE id_ganado = ?
                        """,
                        idGanado
                );

        if (bovinos.isEmpty()) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        List<Map<String, Object>> fotos =
                jdbcTemplate.queryForList(
                        """
                        SELECT
                            id_foto,
                            id_ganado,
                            ruta_foto,
                            url_fuente,
                            credito_fuente,
                            licencia_uso,
                            es_principal,
                            descripcion,
                            fecha_registro
                        FROM dbo.fotos_ganado
                        WHERE id_ganado = ?
                          AND activo = 1
                        ORDER BY
                            es_principal DESC,
                            id_foto DESC
                        """,
                        idGanado
                );

        List<Map<String, Object>> historialVeterinario =
                jdbcTemplate.queryForList(
                        """
                        SELECT
                            id_registro,
                            codigo_registro,
                            id_ganado,
                            codigo_arete,
                            nombre_apodo,
                            sexo,
                            tipo_atencion,
                            fecha_atencion,
                            veterinario,
                            diagnostico,
                            tratamiento,
                            medicamento_vacuna,
                            dosis,
                            proxima_atencion,
                            estado,
                            observaciones,
                            costo
                        FROM dbo.vw_veterinaria_control
                        WHERE id_ganado = ?
                        ORDER BY
                            fecha_atencion DESC,
                            id_registro DESC
                        """,
                        idGanado
                );

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "bovino",
                bovinos.get(0)
        );

        respuesta.put(
                "fotos",
                fotos
        );

        respuesta.put(
                "historial_veterinario",
                historialVeterinario
        );

        return respuesta;
    }


    /* =========================================================
       6. CLIENTES DISPONIBLES PARA PARTICIPAR
       ========================================================= */

    @GetMapping("/catalogos/clientes")
    public List<Map<String, Object>> clientes() {

        return jdbcTemplate.queryForList(
                """
                SELECT
                    id_cliente,
                    nombre,
                    ruc,
                    cedula,
                    telefono,
                    email,
                    direccion,
                    municipio,
                    tipo_cliente,
                    contacto
                FROM dbo.clientes
                WHERE activo = 1
                ORDER BY nombre
                """
        );
    }


    /* =========================================================
       7. PARTICIPANTES DE UNA SUBASTA
       ========================================================= */

    @GetMapping("/{idSubasta}/participantes")
    public List<Map<String, Object>> participantes(
            @PathVariable Integer idSubasta) {

        return jdbcTemplate.queryForList(
                """
                SELECT
                    sp.id_participante,
                    sp.id_subasta,
                    sp.id_cliente,
                    sp.numero_paleta,
                    sp.estado,
                    sp.fecha_registro,
                    sp.observaciones,

                    c.nombre AS cliente,
                    c.ruc,
                    c.cedula,
                    c.telefono,
                    c.email,
                    c.municipio,
                    c.tipo_cliente

                FROM dbo.subasta_participantes sp

                INNER JOIN dbo.clientes c
                    ON c.id_cliente =
                       sp.id_cliente

                WHERE sp.id_subasta = ?

                ORDER BY
                    CASE sp.estado
                        WHEN N'Habilitado' THEN 1
                        WHEN N'Retirado' THEN 2
                        ELSE 3
                    END,
                    sp.numero_paleta
                """,
                idSubasta
        );
    }


    /* =========================================================
       8. LOTES DE UNA SUBASTA
       ========================================================= */

    @GetMapping("/{idSubasta}/lotes")
    public List<Map<String, Object>> lotes(
            @PathVariable Integer idSubasta) {

        return jdbcTemplate.queryForList(
                """
                SELECT *
                FROM dbo.vw_subasta_lotes_detalle
                WHERE id_subasta = ?
                ORDER BY numero_lote
                """,
                idSubasta
        );
    }


    /* =========================================================
       9. HISTORIAL DE PUJAS DE UN LOTE
       ========================================================= */

    @GetMapping("/lotes/{idLote}/pujas")
    public List<Map<String, Object>> pujas(
            @PathVariable Integer idLote) {

        return jdbcTemplate.queryForList(
                """
                SELECT
                    p.id_puja,
                    p.id_subasta,
                    p.id_lote,
                    p.id_participante,
                    sp.numero_paleta,
                    c.id_cliente,
                    c.nombre AS participante,
                    p.monto,
                    p.fecha_hora,
                    p.estado,
                    p.id_empleado_registro,
                    p.observaciones

                FROM dbo.subasta_pujas p

                INNER JOIN dbo.subasta_participantes sp
                    ON sp.id_participante =
                       p.id_participante
                   AND sp.id_subasta =
                       p.id_subasta

                INNER JOIN dbo.clientes c
                    ON c.id_cliente =
                       sp.id_cliente

                WHERE p.id_lote = ?

                ORDER BY
                    p.fecha_hora DESC,
                    p.id_puja DESC
                """,
                idLote
        );
    }


    /* =========================================================
       10. RESULTADOS DE UNA SUBASTA
       ========================================================= */

    @GetMapping("/{idSubasta}/resultados")
    public List<Map<String, Object>> resultados(
            @PathVariable Integer idSubasta) {

        return jdbcTemplate.queryForList(
                """
                SELECT *
                FROM dbo.vw_subasta_resultados
                WHERE id_subasta = ?
                ORDER BY numero_lote
                """,
                idSubasta
        );
    }


    /* =========================================================
       11. CREAR SUBASTA
       ========================================================= */

    @PostMapping
    public Object crear(
            @RequestBody Map<String, Object> datos) {

        try {

            String codigo =
                    obligatorio(
                            datos,
                            "codigo_subasta"
                    );

            String nombre =
                    obligatorio(
                            datos,
                            "nombre"
                    );

            Timestamp fechaProgramada =
                    fechaHora(
                            obligatorio(
                                    datos,
                                    "fecha_programada"
                            )
                    );

            String lugar =
                    obligatorio(
                            datos,
                            "lugar"
                    );

            String descripcion =
                    opcional(
                            datos,
                            "descripcion"
                    );

            Integer idEmpleado =
                    enteroOpcional(
                            datos.get(
                                    "id_empleado_creador"
                            )
                    );

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_Crear
                        @codigo_subasta = ?,
                        @nombre = ?,
                        @fecha_programada = ?,
                        @lugar = ?,
                        @descripcion = ?,
                        @id_empleado_creador = ?
                    """,
                    codigo,
                    nombre,
                    fechaProgramada,
                    lugar,
                    descripcion,
                    idEmpleado
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
        catch (Exception error) {
            return errorDatos(
                    mensaje(
                            error,
                            "Los datos de la subasta no son válidos."
                    )
            );
        }
    }


    /* =========================================================
       12. REGISTRAR FOTOGRAFÍA
       ========================================================= */

    @PostMapping("/bovinos/{idGanado}/fotos")
    public Object registrarFoto(
            @PathVariable Integer idGanado,
            @RequestBody Map<String, Object> datos) {

        try {

            String rutaFoto =
                    obligatorio(
                            datos,
                            "ruta_foto"
                    );

            String urlFuente =
                    opcional(
                            datos,
                            "url_fuente"
                    );

            String creditoFuente =
                    opcional(
                            datos,
                            "credito_fuente"
                    );

            String licenciaUso =
                    opcional(
                            datos,
                            "licencia_uso"
                    );

            Boolean principal =
                    booleano(
                            datos.get(
                                    "es_principal"
                            ),
                            true
                    );

            String descripcion =
                    opcional(
                            datos,
                            "descripcion"
                    );

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_RegistrarFotoGanado
                        @id_ganado = ?,
                        @ruta_foto = ?,
                        @url_fuente = ?,
                        @credito_fuente = ?,
                        @licencia_uso = ?,
                        @es_principal = ?,
                        @descripcion = ?
                    """,
                    idGanado,
                    rutaFoto,
                    urlFuente,
                    creditoFuente,
                    licenciaUso,
                    principal,
                    descripcion
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
        catch (Exception error) {
            return errorDatos(
                    mensaje(
                            error,
                            "Los datos de la fotografía no son válidos."
                    )
            );
        }
    }


    /* =========================================================
       13. INSCRIBIR PARTICIPANTE
       ========================================================= */

    @PostMapping("/{idSubasta}/participantes")
    public Object inscribirParticipante(
            @PathVariable Integer idSubasta,
            @RequestBody Map<String, Object> datos) {

        try {

            Integer idCliente =
                    enteroObligatorio(
                            datos,
                            "id_cliente"
                    );

            String numeroPaleta =
                    obligatorio(
                            datos,
                            "numero_paleta"
                    );

            String observaciones =
                    opcional(
                            datos,
                            "observaciones"
                    );

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_InscribirParticipante
                        @id_subasta = ?,
                        @id_cliente = ?,
                        @numero_paleta = ?,
                        @observaciones = ?
                    """,
                    idSubasta,
                    idCliente,
                    numeroPaleta,
                    observaciones
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
        catch (Exception error) {
            return errorDatos(
                    mensaje(
                            error,
                            "Los datos del participante no son válidos."
                    )
            );
        }
    }


    /* =========================================================
       14. AGREGAR BOVINO COMO LOTE
       ========================================================= */

    @PostMapping("/{idSubasta}/lotes")
    public Object agregarLote(
            @PathVariable Integer idSubasta,
            @RequestBody Map<String, Object> datos) {

        try {

            Integer idGanado =
                    enteroObligatorio(
                            datos,
                            "id_ganado"
                    );

            Integer numeroLote =
                    enteroObligatorio(
                            datos,
                            "numero_lote"
                    );

            BigDecimal precioBase =
                    decimalObligatorio(
                            datos,
                            "precio_base"
                    );

            BigDecimal incrementoMinimo =
                    decimalObligatorio(
                            datos,
                            "incremento_minimo"
                    );

            BigDecimal precioReserva =
                    decimalOpcional(
                            datos.get(
                                    "precio_reserva"
                            )
                    );

            String observaciones =
                    opcional(
                            datos,
                            "observaciones"
                    );

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_AgregarLote
                        @id_subasta = ?,
                        @id_ganado = ?,
                        @numero_lote = ?,
                        @precio_base = ?,
                        @incremento_minimo = ?,
                        @precio_reserva = ?,
                        @observaciones = ?
                    """,
                    idSubasta,
                    idGanado,
                    numeroLote,
                    precioBase,
                    incrementoMinimo,
                    precioReserva,
                    observaciones
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
        catch (Exception error) {
            return errorDatos(
                    mensaje(
                            error,
                            "Los datos del lote no son válidos."
                    )
            );
        }
    }


    /* =========================================================
       15. PROGRAMAR SUBASTA
       ========================================================= */

    @PostMapping("/{idSubasta}/programar")
    public Object programar(
            @PathVariable Integer idSubasta) {

        try {

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_Programar
                        @id_subasta = ?
                    """,
                    idSubasta
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
    }


    /* =========================================================
       16. INICIAR SUBASTA
       ========================================================= */

    @PostMapping("/{idSubasta}/iniciar")
    public Object iniciar(
            @PathVariable Integer idSubasta) {

        try {

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_Iniciar
                        @id_subasta = ?
                    """,
                    idSubasta
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
    }


    /* =========================================================
       17. ABRIR LOTE PARA PUJAS
       ========================================================= */

    @PostMapping("/{idSubasta}/lotes/{idLote}/abrir")
    public Object abrirLote(
            @PathVariable Integer idSubasta,
            @PathVariable Integer idLote) {

        try {

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_AbrirLote
                        @id_subasta = ?,
                        @id_lote = ?
                    """,
                    idSubasta,
                    idLote
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
    }


    /* =========================================================
       18. REGISTRAR PUJA
       ========================================================= */

    @PostMapping("/{idSubasta}/lotes/{idLote}/pujas")
    public Object registrarPuja(
            @PathVariable Integer idSubasta,
            @PathVariable Integer idLote,
            @RequestBody Map<String, Object> datos) {

        try {

            Integer idParticipante =
                    enteroObligatorio(
                            datos,
                            "id_participante"
                    );

            BigDecimal monto =
                    decimalObligatorio(
                            datos,
                            "monto"
                    );

            Integer idEmpleado =
                    resolverEmpleadoOperacion(
                            datos
                    );

            String observaciones =
                    opcional(
                            datos,
                            "observaciones"
                    );

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_RegistrarPuja
                        @id_subasta = ?,
                        @id_lote = ?,
                        @id_participante = ?,
                        @monto = ?,
                        @id_empleado_registro = ?,
                        @observaciones = ?
                    """,
                    idSubasta,
                    idLote,
                    idParticipante,
                    monto,
                    idEmpleado,
                    observaciones
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
        catch (Exception error) {
            return errorDatos(
                    mensaje(
                            error,
                            "Los datos de la puja no son válidos."
                    )
            );
        }
    }


    /* =========================================================
       19. CERRAR LOTE
       Adjudica + venta ganado + baja + Finanzas.
       ========================================================= */

    @PostMapping("/{idSubasta}/lotes/{idLote}/cerrar")
    public Object cerrarLote(
            @PathVariable Integer idSubasta,
            @PathVariable Integer idLote,
            @RequestBody Map<String, Object> datos) {

        try {

            Integer idEmpleado =
                    resolverEmpleadoOperacion(
                            datos
                    );

            String metodoPago =
                    opcional(
                            datos,
                            "metodo_pago"
                    );

            if (metodoPago == null) {
                metodoPago = "Efectivo";
            }

            return primerResultado(
                    """
                    EXEC dbo.sp_Subasta_CerrarLote
                        @id_subasta = ?,
                        @id_lote = ?,
                        @id_empleado = ?,
                        @metodo_pago = ?
                    """,
                    idSubasta,
                    idLote,
                    idEmpleado,
                    metodoPago
            );

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
        catch (Exception error) {
            return errorDatos(
                    mensaje(
                            error,
                            "No se pudo cerrar el lote."
                    )
            );
        }
    }


    /* =========================================================
       20. FINALIZAR SUBASTA
       ========================================================= */

    @PostMapping("/{idSubasta}/finalizar")
    public Object finalizar(
            @PathVariable Integer idSubasta) {

        try {

            /*
             * El procedimiento devuelve más de un SELECT.
             * Ejecutamos el procedimiento y después consultamos
             * las vistas para devolver una respuesta estable.
             */
            jdbcTemplate.queryForList(
                    """
                    EXEC dbo.sp_Subasta_Finalizar
                        @id_subasta = ?
                    """,
                    idSubasta
            );

            Map<String, Object> respuesta =
                    new LinkedHashMap<>();

            List<Map<String, Object>> resumen =
                    jdbcTemplate.queryForList(
                            """
                            SELECT *
                            FROM dbo.vw_subastas_resumen
                            WHERE id_subasta = ?
                            """,
                            idSubasta
                    );

            List<Map<String, Object>> resultados =
                    jdbcTemplate.queryForList(
                            """
                            SELECT *
                            FROM dbo.vw_subasta_resultados
                            WHERE id_subasta = ?
                            ORDER BY numero_lote
                            """,
                            idSubasta
                    );

            respuesta.put(
                    "subasta",
                    resumen.isEmpty()
                            ? null
                            : resumen.get(0)
            );

            respuesta.put(
                    "resultados",
                    resultados
            );

            respuesta.put(
                    "mensaje",
                    "Subasta finalizada correctamente."
            );

            return respuesta;

        }
        catch (DataAccessException error) {
            return errorSQL(error);
        }
    }


    /* =========================================================
       HELPERS
       ========================================================= */

    private Object primerResultado(
            String sql,
            Object... parametros) {

        List<Map<String, Object>> resultado =
                jdbcTemplate.queryForList(
                        sql,
                        parametros
                );

        if (resultado.isEmpty()) {

            Map<String, Object> respuesta =
                    new LinkedHashMap<>();

            respuesta.put(
                    "ok",
                    true
            );

            return respuesta;
        }

        return resultado.get(0);
    }


    /* =========================================================
       RESOLVER EMPLEADO PARA TRAZABILIDAD
       ---------------------------------------------------------
       - Usuarios normales: usa su empleado asociado.
       - Usuarios de compatibilidad/demo: usa el operador
         técnico de Subastas creado por el script SQL.
       ========================================================= */

    private Integer resolverEmpleadoOperacion(
            Map<String, Object> datos) {

        Integer idEmpleado =
                enteroOpcional(
                        datos.get("id_empleado")
                );

        if (idEmpleado == null) {
            idEmpleado =
                    enteroOpcional(
                            datos.get("id_empleado_registro")
                    );
        }

        if (
                idEmpleado != null &&
                empleadoActivo(idEmpleado)
        ) {
            return idEmpleado;
        }

        String origen =
                texto(
                        datos.get("origen")
                );

        /*
         * Para una sesión real de BD sí podemos resolver
         * el empleado a través de dbo.usuarios.
         * No usamos el id_usuario de las cuentas demo porque
         * esos ids son solo de compatibilidad y podrían coincidir
         * con un usuario real de la tabla.
         */
        if (
                origen == null ||
                !origen.equalsIgnoreCase("COMPATIBILIDAD")
        ) {

            Integer idUsuario =
                    enteroOpcional(
                            datos.get("id_usuario")
                    );

            if (idUsuario != null) {

                List<Integer> empleadosUsuario =
                        jdbcTemplate.queryForList(
                                """
                                SELECT TOP (1)
                                    u.id_empleado
                                FROM dbo.usuarios u
                                INNER JOIN dbo.empleados e
                                    ON e.id_empleado = u.id_empleado
                                WHERE u.id_usuario = ?
                                  AND e.Estado_actividad = 1
                                """,
                                Integer.class,
                                idUsuario
                        );

                if (!empleadosUsuario.isEmpty()) {
                    return empleadosUsuario.get(0);
                }
            }

            String username =
                    texto(
                            datos.get("username")
                    );

            if (username != null) {

                List<Integer> empleadosUsuario =
                        jdbcTemplate.queryForList(
                                """
                                SELECT TOP (1)
                                    u.id_empleado
                                FROM dbo.usuarios u
                                INNER JOIN dbo.empleados e
                                    ON e.id_empleado = u.id_empleado
                                WHERE LOWER(u.nombre_usuario) = LOWER(?)
                                  AND e.Estado_actividad = 1
                                """,
                                Integer.class,
                                username
                        );

                if (!empleadosUsuario.isEmpty()) {
                    return empleadosUsuario.get(0);
                }
            }
        }

        /*
         * Fallback controlado para las cuentas antiguas/demo.
         * No se usa un cliente ni un comprador: se usa un
         * empleado técnico exclusivo para trazabilidad.
         */
        List<Integer> operadorTecnico =
                jdbcTemplate.queryForList(
                        """
                        SELECT TOP (1)
                            e.id_empleado
                        FROM dbo.empleados e
                        INNER JOIN dbo.personas p
                            ON p.id_persona = e.id_persona
                        WHERE p.cedula = N'GB-SUB-SYS-0001'
                          AND e.Estado_actividad = 1
                        ORDER BY e.id_empleado
                        """,
                        Integer.class
                );

        if (!operadorTecnico.isEmpty()) {
            return operadorTecnico.get(0);
        }

        throw new IllegalArgumentException(
                "No se pudo identificar al empleado que registra la operación. " +
                "Ejecuta Subastas_BD_04_Operador_Sistema.sql."
        );
    }


    private boolean empleadoActivo(
            Integer idEmpleado) {

        Integer total =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM dbo.empleados
                        WHERE id_empleado = ?
                          AND Estado_actividad = 1
                        """,
                        Integer.class,
                        idEmpleado
                );

        return total != null && total > 0;
    }


    private String obligatorio(
            Map<String, Object> datos,
            String campo) {

        String valor =
                texto(
                        datos.get(campo)
                );

        if (valor == null) {
            throw new IllegalArgumentException(
                    "El campo "
                    + campo
                    + " es obligatorio."
            );
        }

        return valor;
    }


    private String opcional(
            Map<String, Object> datos,
            String campo) {

        return texto(
                datos.get(campo)
        );
    }


    private String texto(Object valor) {

        if (valor == null) {
            return null;
        }

        String resultado =
                String.valueOf(valor)
                        .trim();

        return resultado.isEmpty()
                ? null
                : resultado;
    }


    private Integer enteroObligatorio(
            Map<String, Object> datos,
            String campo) {

        Integer valor =
                enteroOpcional(
                        datos.get(campo)
                );

        if (valor == null) {
            throw new IllegalArgumentException(
                    "El campo "
                    + campo
                    + " es obligatorio."
            );
        }

        return valor;
    }


    private Integer enteroOpcional(
            Object valor) {

        if (valor == null) {
            return null;
        }

        String texto =
                String.valueOf(valor)
                        .trim();

        if (texto.isEmpty()
                || texto.equalsIgnoreCase("null")) {
            return null;
        }

        return Integer.valueOf(texto);
    }


    private BigDecimal decimalObligatorio(
            Map<String, Object> datos,
            String campo) {

        BigDecimal valor =
                decimalOpcional(
                        datos.get(campo)
                );

        if (valor == null) {
            throw new IllegalArgumentException(
                    "El campo "
                    + campo
                    + " es obligatorio."
            );
        }

        return valor;
    }


    private BigDecimal decimalOpcional(
            Object valor) {

        if (valor == null) {
            return null;
        }

        String texto =
                String.valueOf(valor)
                        .trim()
                        .replace(",", ".");

        if (texto.isEmpty()
                || texto.equalsIgnoreCase("null")) {
            return null;
        }

        return new BigDecimal(texto);
    }


    private Boolean booleano(
            Object valor,
            boolean predeterminado) {

        if (valor == null) {
            return predeterminado;
        }

        String texto =
                String.valueOf(valor)
                        .trim();

        if (texto.equalsIgnoreCase("true")
                || texto.equals("1")
                || texto.equalsIgnoreCase("si")
                || texto.equalsIgnoreCase("sí")) {
            return true;
        }

        if (texto.equalsIgnoreCase("false")
                || texto.equals("0")
                || texto.equalsIgnoreCase("no")) {
            return false;
        }

        return predeterminado;
    }


    private Timestamp fechaHora(
            String valor) {

        String texto =
                valor.trim()
                        .replace("T", " ");

        if (texto.length() == 10) {
            texto += " 00:00:00";
        }
        else if (texto.length() == 16) {
            texto += ":00";
        }

        return Timestamp.valueOf(texto);
    }


    private ResponseEntity<Map<String, Object>> errorSQL(
            DataAccessException error) {

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "error",
                obtenerMensajeSQL(error)
        );

        return ResponseEntity
                .badRequest()
                .body(respuesta);
    }


    private ResponseEntity<Map<String, Object>> errorDatos(
            String texto) {

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "error",
                texto
        );

        return ResponseEntity
                .badRequest()
                .body(respuesta);
    }


    private String obtenerMensajeSQL(
            Throwable error) {

        Throwable actual = error;

        while (actual.getCause() != null) {
            actual = actual.getCause();
        }

        String texto =
                actual.getMessage();

        if (texto == null
                || texto.isBlank()) {

            texto =
                    error.getMessage();
        }

        if (texto == null
                || texto.isBlank()) {

            return "Ocurrió un error en la base de datos.";
        }

        /*
         * Los mensajes generados por THROW en SQL Server
         * normalmente aparecen dentro del texto del driver.
         * Quitamos algunos prefijos comunes para mostrar un
         * mensaje más limpio en la interfaz.
         */
        int posicion =
                texto.indexOf(
                        "The statement has been terminated."
                );

        if (posicion > 0) {
            texto =
                    texto.substring(
                            0,
                            posicion
                    ).trim();
        }

        return texto;
    }


    private String mensaje(
            Exception error,
            String predeterminado) {

        String texto =
                error.getMessage();

        if (texto == null
                || texto.isBlank()) {

            return predeterminado;
        }

        return texto;
    }


    private int numero(
            Integer valor) {

        return valor == null
                ? 0
                : valor;
    }
}
