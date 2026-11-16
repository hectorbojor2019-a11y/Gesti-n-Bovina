package com.gestionbovina.gestionbovina.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/personal")
@CrossOrigin(origins = "*")
public class PersonalController {

    private final JdbcTemplate jdbcTemplate;

    public PersonalController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /* =========================================================
       ESTADO
       ========================================================= */

    @GetMapping("/estado")
    public Map<String, Object> estado() {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        Integer empleados = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.empleados
                WHERE COALESCE(es_tecnico,0) = 0
                """,
                Integer.class
        );

        respuesta.put("ok", true);
        respuesta.put("modulo", "PERSONAL");
        respuesta.put("empleados", empleados == null ? 0 : empleados);

        return respuesta;
    }


    /* =========================================================
       ESTADÍSTICAS
       ========================================================= */

    @GetMapping("/estadisticas")
    public Map<String, Object> estadisticas() {

        return jdbcTemplate.queryForMap("""
                SELECT
                    COUNT(*) AS total,

                    SUM(
                        CASE
                            WHEN e.Estado_actividad = 1
                            THEN 1 ELSE 0
                        END
                    ) AS activos,

                    SUM(
                        CASE
                            WHEN e.Estado_actividad = 0
                            THEN 1 ELSE 0
                        END
                    ) AS inactivos,

                    COUNT(
                        DISTINCT
                        CASE
                            WHEN a.id_area IS NOT NULL
                            THEN a.id_area
                        END
                    ) AS areas

                FROM dbo.empleados e

                INNER JOIN dbo.cargos c
                    ON c.id_cargo = e.id_cargo

                LEFT JOIN dbo.areas a
                    ON a.id_area = c.id_area

                WHERE COALESCE(e.es_tecnico,0) = 0
                """);
    }


    /* =========================================================
       EMPLEADOS
       ========================================================= */

    @GetMapping("/empleados")
    public List<Map<String, Object>> empleados() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_empleado,
                    codigo_empleado,
                    nombre_completo,
                    cedula,
                    cargo,
                    area,
                    telefono,
                    fecha_ingreso,
                    salario_actual,
                    turno,
                    CASE
                        WHEN Estado_actividad = 1 THEN N'Activo'
                        ELSE N'Inactivo'
                    END AS estado,
                    tipo_contrato
                FROM dbo.vw_personal_control
                WHERE COALESCE(es_tecnico,0) = 0
                ORDER BY
                    Estado_actividad DESC,
                    nombre_completo
                """);
    }


    @GetMapping("/empleados/activos")
    public List<Map<String, Object>> empleadosActivos() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_empleado,
                    codigo_empleado,
                    nombre_completo,
                    cedula,
                    cargo,
                    area,
                    telefono,
                    fecha_ingreso,
                    salario_actual,
                    turno,
                    tipo_contrato,
                    numero_inss
                FROM dbo.vw_personal_control
                WHERE COALESCE(es_tecnico,0) = 0
                  AND Estado_actividad = 1
                ORDER BY nombre_completo
                """);
    }


    @GetMapping("/empleados/{idEmpleado}")
    public Map<String, Object> detalleEmpleado(
            @PathVariable Integer idEmpleado) {

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT
                    id_empleado,
                    codigo_empleado,

                    CONCAT(
                        primer_nombre,
                        CASE
                            WHEN NULLIF(segundo_nombre,N'') IS NULL
                            THEN N''
                            ELSE N' ' + segundo_nombre
                        END
                    ) AS nombres,

                    CONCAT(
                        primer_apellido,
                        CASE
                            WHEN NULLIF(segundo_apellido,N'') IS NULL
                            THEN N''
                            ELSE N' ' + segundo_apellido
                        END
                    ) AS apellidos,

                    nombre_completo,
                    cedula,
                    fecha_nacimiento,
                    sexo,
                    telefono,
                    email,

                    id_cargo,
                    cargo,
                    id_area,
                    area,

                    fecha_ingreso,
                    salario_actual,
                    turno,

                    CASE
                        WHEN Estado_actividad = 1 THEN N'Activo'
                        ELSE N'Inactivo'
                    END AS estado,

                    tipo_contrato,
                    direccion,
                    contacto_emergencia,
                    telefono_emergencia,
                    numero_inss,
                    observaciones

                FROM dbo.vw_personal_control

                WHERE id_empleado = ?
                  AND COALESCE(es_tecnico,0) = 0
                """,
                idEmpleado
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Empleado no encontrado."
            );
        }

        return filas.get(0);
    }


    /* =========================================================
       CATÁLOGOS
       ========================================================= */

    @GetMapping("/cargos")
    public List<Map<String, Object>> cargos() {

        return jdbcTemplate.queryForList("""
                SELECT
                    c.id_cargo,
                    c.codigo_cargo,
                    c.nombre_cargo,
                    c.salario_base,
                    a.id_area,
                    a.nombre_area
                FROM dbo.cargos c
                LEFT JOIN dbo.areas a
                    ON a.id_area = c.id_area
                WHERE c.activo = 1
                  AND c.codigo_cargo <> N'FIN-001'
                ORDER BY c.nombre_cargo
                """);
    }


    @GetMapping("/areas")
    public List<Map<String, Object>> areas() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_area,
                    codigo_area,
                    nombre_area
                FROM dbo.areas
                WHERE activo = 1
                ORDER BY nombre_area
                """);
    }


    /* =========================================================
       REGISTRAR EMPLEADO
       ========================================================= */

    @PostMapping("/empleados")
    @Transactional
    public Map<String, Object> registrarEmpleado(
            @RequestBody Map<String, Object> datos) {

        DatosEmpleado d = leerDatos(datos);

        validarDatos(d, null);

        try {

            Nombres nombres = separarNombres(d.nombres());
            Nombres apellidos = separarNombres(d.apellidos());

            Integer idPersona = jdbcTemplate.queryForObject("""
                    INSERT INTO dbo.personas (
                        primer_nombre,
                        segundo_nombre,
                        primer_apellido,
                        segundo_apellido,
                        cedula,
                        sexo,
                        Genero,
                        fecha_nacimiento,
                        tel1,
                        tel2,
                        Estado_civil,
                        telefono_emergencia1,
                        telefono_emergencia2,
                        email,
                        tipo_sangre,
                        direccion,
                        municipio,
                        alergias,
                        fecha_registro,
                        contacto_emergencia
                    )
                    OUTPUT INSERTED.id_persona
                    VALUES (
                        ?, ?, ?, ?, ?,
                        ?, ?,
                        ?,
                        ?, NULL, NULL,
                        ?, ?,
                        ?,
                        NULL,
                        ?,
                        NULL,
                        N'No registradas',
                        GETDATE(),
                        ?
                    )
                    """,
                    Integer.class,
                    nombres.primero(),
                    nombres.segundo(),
                    apellidos.primero(),
                    apellidos.segundo(),
                    d.cedula(),
                    d.sexo(),
                    generoTexto(d.sexo()),
                    d.fechaNacimiento(),
                    d.telefono(),
                    d.telefonoEmergencia(),
                    "00000000",
                    d.email(),
                    d.direccion(),
                    d.contactoEmergencia()
            );

            Integer idEmpleado = jdbcTemplate.queryForObject("""
                    INSERT INTO dbo.empleados (
                        id_persona,
                        id_cargo,
                        fecha_ingreso,
                        fecha_salida,
                        numero_inss,
                        salario_actual,
                        estado_empleado,
                        tipo_licencia,
                        Estado_actividad,
                        observaciones,
                        codigo_empleado,
                        turno,
                        es_tecnico
                    )
                    OUTPUT INSERTED.id_empleado
                    VALUES (
                        ?, ?, ?, NULL,
                        ?, ?,
                        ?,
                        NULL,
                        ?,
                        ?,
                        ?, ?,
                        0
                    )
                    """,
                    Integer.class,
                    idPersona,
                    d.idCargo(),
                    d.fechaIngreso(),
                    d.numeroInss(),
                    d.salario(),
                    d.activo() ? "Activo" : "Inactivo",
                    d.activo() ? 1 : 0,
                    d.observaciones(),
                    d.codigoEmpleado(),
                    d.turno()
            );

            crearContrato(
                    idEmpleado,
                    d.idCargo(),
                    d.tipoContrato(),
                    d.fechaIngreso(),
                    d.salario(),
                    d.activo()
            );

            registrarHistorialEstado(
                    idEmpleado,
                    d.activo() ? "Activo" : "Inactivo",
                    d.fechaIngreso(),
                    d.activo()
                            ? "Registro inicial del empleado"
                            : "Registro inicial como inactivo"
            );

            return respuestaOk(
                    "Empleado registrado correctamente.",
                    idEmpleado
            );

        } catch (DataIntegrityViolationException error) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se pudo guardar el empleado. Verifica cédula, correo, código y número INSS.",
                    error
            );
        }
    }


    /* =========================================================
       MODIFICAR EMPLEADO
       ========================================================= */

    @PutMapping("/empleados/{idEmpleado}")
    @Transactional
    public Map<String, Object> modificarEmpleado(
            @PathVariable Integer idEmpleado,
            @RequestBody Map<String, Object> datos) {

        Map<String, Object> actual = empleadoActual(idEmpleado);

        DatosEmpleado d = leerDatos(datos);

        validarDatos(d, idEmpleado);

        Nombres nombres = separarNombres(d.nombres());
        Nombres apellidos = separarNombres(d.apellidos());

        jdbcTemplate.update("""
                UPDATE dbo.personas
                SET
                    primer_nombre = ?,
                    segundo_nombre = ?,
                    primer_apellido = ?,
                    segundo_apellido = ?,
                    cedula = ?,
                    sexo = ?,
                    Genero = ?,
                    fecha_nacimiento = ?,
                    tel1 = ?,
                    email = ?,
                    direccion = ?,
                    contacto_emergencia = ?,
                    telefono_emergencia1 = ?
                WHERE id_persona = ?
                """,
                nombres.primero(),
                nombres.segundo(),
                apellidos.primero(),
                apellidos.segundo(),
                d.cedula(),
                d.sexo(),
                generoTexto(d.sexo()),
                d.fechaNacimiento(),
                d.telefono(),
                d.email(),
                d.direccion(),
                d.contactoEmergencia(),
                d.telefonoEmergencia(),
                actual.get("id_persona")
        );

        boolean estabaActivo =
                esActivo(actual.get("Estado_actividad"));

        jdbcTemplate.update("""
                UPDATE dbo.empleados
                SET
                    id_cargo = ?,
                    fecha_ingreso = ?,
                    fecha_salida =
                        CASE
                            WHEN ? = 1 THEN NULL
                            ELSE COALESCE(fecha_salida, CONVERT(date,GETDATE()))
                        END,
                    numero_inss = ?,
                    salario_actual = ?,
                    estado_empleado = ?,
                    Estado_actividad = ?,
                    observaciones = ?,
                    codigo_empleado = ?,
                    turno = ?
                WHERE id_empleado = ?
                """,
                d.idCargo(),
                d.fechaIngreso(),
                d.activo() ? 1 : 0,
                d.numeroInss(),
                d.salario(),
                d.activo() ? "Activo" : "Inactivo",
                d.activo() ? 1 : 0,
                d.observaciones(),
                d.codigoEmpleado(),
                d.turno(),
                idEmpleado
        );

        actualizarContrato(
                idEmpleado,
                d.idCargo(),
                d.tipoContrato(),
                d.fechaIngreso(),
                d.salario(),
                d.activo()
        );

        if (estabaActivo != d.activo()) {

            cerrarHistorialVigente(idEmpleado);

            registrarHistorialEstado(
                    idEmpleado,
                    d.activo() ? "Activo" : "Inactivo",
                    LocalDate.now(),
                    d.activo()
                            ? "Reactivación desde módulo Personal"
                            : "Baja desde modificación de empleado"
            );
        }

        return respuestaOk(
                "Empleado modificado correctamente.",
                idEmpleado
        );
    }


    /* =========================================================
       DAR DE BAJA
       ========================================================= */

    @PostMapping("/empleados/{idEmpleado}/baja")
    @Transactional
    public Map<String, Object> darDeBaja(
            @PathVariable Integer idEmpleado,
            @RequestBody(required = false) Map<String, Object> datos) {

        Map<String, Object> actual = empleadoActual(idEmpleado);

        if (!esActivo(actual.get("Estado_actividad"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado ya se encuentra inactivo."
            );
        }

        String motivo =
                datos == null
                        ? null
                        : texto(datos.get("motivo"));

        if (motivo == null) {
            motivo = "Baja registrada desde módulo Personal";
        }

        jdbcTemplate.update("""
                UPDATE dbo.empleados
                SET
                    Estado_actividad = 0,
                    estado_empleado = N'Inactivo',
                    fecha_salida = CONVERT(date,GETDATE())
                WHERE id_empleado = ?
                """,
                idEmpleado
        );

        jdbcTemplate.update("""
                UPDATE dbo.contratos
                SET
                    estado = N'Finalizado',
                    fecha_fin = COALESCE(fecha_fin,CONVERT(date,GETDATE()))
                WHERE id_empleado = ?
                  AND estado = N'Activo'
                """,
                idEmpleado
        );

        cerrarHistorialVigente(idEmpleado);

        registrarHistorialEstado(
                idEmpleado,
                "Inactivo",
                LocalDate.now(),
                motivo
        );

        return respuestaOk(
                "Empleado dado de baja correctamente. El historial fue conservado.",
                idEmpleado
        );
    }


    /* =========================================================
       REACTIVAR
       ========================================================= */

    @PostMapping("/empleados/{idEmpleado}/reactivar")
    @Transactional
    public Map<String, Object> reactivar(
            @PathVariable Integer idEmpleado) {

        Map<String, Object> actual = empleadoActual(idEmpleado);

        if (esActivo(actual.get("Estado_actividad"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado ya está activo."
            );
        }

        jdbcTemplate.update("""
                UPDATE dbo.empleados
                SET
                    Estado_actividad = 1,
                    estado_empleado = N'Activo',
                    fecha_salida = NULL
                WHERE id_empleado = ?
                """,
                idEmpleado
        );

        List<Map<String, Object>> contratos = jdbcTemplate.queryForList("""
                SELECT TOP 1 id_contrato
                FROM dbo.contratos
                WHERE id_empleado = ?
                ORDER BY id_contrato DESC
                """,
                idEmpleado
        );

        if (!contratos.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE dbo.contratos
                    SET
                        estado = N'Activo',
                        fecha_fin = NULL
                    WHERE id_contrato = ?
                    """,
                    contratos.get(0).get("id_contrato")
            );
        }

        cerrarHistorialVigente(idEmpleado);

        registrarHistorialEstado(
                idEmpleado,
                "Activo",
                LocalDate.now(),
                "Empleado reactivado desde módulo Personal"
        );

        return respuestaOk(
                "Empleado reactivado correctamente.",
                idEmpleado
        );
    }


    /* =========================================================
       HELPERS
       ========================================================= */

    private Map<String, Object> empleadoActual(Integer idEmpleado) {

        List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                SELECT
                    e.id_empleado,
                    e.id_persona,
                    e.Estado_actividad
                FROM dbo.empleados e
                WHERE e.id_empleado = ?
                  AND COALESCE(e.es_tecnico,0) = 0
                """,
                idEmpleado
        );

        if (filas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Empleado no encontrado."
            );
        }

        return filas.get(0);
    }


    private void validarDatos(
            DatosEmpleado d,
            Integer idEmpleado) {

        if (d.codigoEmpleado() == null ||
            d.nombres() == null ||
            d.apellidos() == null ||
            d.cedula() == null ||
            d.telefono() == null ||
            d.idCargo() == null ||
            d.fechaIngreso() == null ||
            d.salario() == null ||
            d.numeroInss() == null ||
            d.turno() == null ||
            d.tipoContrato() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Completa todos los campos obligatorios."
            );
        }

        if (d.salario().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El salario no puede ser negativo."
            );
        }

        if (d.fechaNacimiento() != null &&
            d.fechaIngreso().isBefore(d.fechaNacimiento())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha de contratación no puede ser anterior a la fecha de nacimiento."
            );
        }

        Integer cargo = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.cargos
                WHERE id_cargo = ?
                  AND activo = 1
                  AND codigo_cargo <> N'FIN-001'
                """,
                Integer.class,
                d.idCargo()
        );

        if (cargo == null || cargo == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El cargo seleccionado no está disponible."
            );
        }

        validarUnicos(d, idEmpleado);
    }


    private void validarUnicos(
            DatosEmpleado d,
            Integer idEmpleado) {

        Integer codigo;

        if (idEmpleado == null) {
            codigo = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.empleados
                    WHERE codigo_empleado = ?
                    """,
                    Integer.class,
                    d.codigoEmpleado()
            );
        } else {
            codigo = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.empleados
                    WHERE codigo_empleado = ?
                      AND id_empleado <> ?
                    """,
                    Integer.class,
                    d.codigoEmpleado(),
                    idEmpleado
            );
        }

        if (codigo != null && codigo > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ya existe un empleado con ese código."
            );
        }

        Integer inss;

        if (idEmpleado == null) {
            inss = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.empleados
                    WHERE numero_inss = ?
                    """,
                    Integer.class,
                    d.numeroInss()
            );
        } else {
            inss = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.empleados
                    WHERE numero_inss = ?
                      AND id_empleado <> ?
                    """,
                    Integer.class,
                    d.numeroInss(),
                    idEmpleado
            );
        }

        if (inss != null && inss > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ya existe un empleado con ese número INSS."
            );
        }

        Integer cedula;

        if (idEmpleado == null) {
            cedula = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.personas
                    WHERE cedula = ?
                    """,
                    Integer.class,
                    d.cedula()
            );
        } else {
            cedula = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM dbo.personas p
                    INNER JOIN dbo.empleados e
                        ON e.id_persona = p.id_persona
                    WHERE p.cedula = ?
                      AND e.id_empleado <> ?
                    """,
                    Integer.class,
                    d.cedula(),
                    idEmpleado
            );
        }

        if (cedula != null && cedula > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ya existe una persona con esa identificación."
            );
        }

        if (d.email() != null) {

            Integer email;

            if (idEmpleado == null) {
                email = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.personas
                        WHERE email = ?
                        """,
                        Integer.class,
                        d.email()
                );
            } else {
                email = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.personas p
                        INNER JOIN dbo.empleados e
                            ON e.id_persona = p.id_persona
                        WHERE p.email = ?
                          AND e.id_empleado <> ?
                        """,
                        Integer.class,
                        d.email(),
                        idEmpleado
                );
            }

            if (email != null && email > 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Ya existe una persona con ese correo electrónico."
                );
            }
        }
    }


    private void crearContrato(
            Integer idEmpleado,
            Integer idCargo,
            String tipoContrato,
            LocalDate fechaInicio,
            BigDecimal salario,
            boolean activo) {

        jdbcTemplate.update("""
                INSERT INTO dbo.contratos (
                    id_empleado,
                    id_cargo,
                    tipo_contrato,
                    fecha_inicio,
                    fecha_fin,
                    salario_mensual,
                    estado,
                    observaciones
                )
                VALUES (
                    ?, ?, ?, ?,
                    ?,
                    ?,
                    ?,
                    N'Contrato creado desde módulo Personal'
                )
                """,
                idEmpleado,
                idCargo,
                tipoContrato,
                fechaInicio,
                activo ? null : LocalDate.now(),
                salario,
                activo ? "Activo" : "Finalizado"
        );
    }


    private void actualizarContrato(
            Integer idEmpleado,
            Integer idCargo,
            String tipoContrato,
            LocalDate fechaInicio,
            BigDecimal salario,
            boolean activo) {

        List<Integer> ids = jdbcTemplate.queryForList("""
                SELECT TOP 1 id_contrato
                FROM dbo.contratos
                WHERE id_empleado = ?
                ORDER BY
                    CASE WHEN estado = N'Activo' THEN 0 ELSE 1 END,
                    id_contrato DESC
                """,
                Integer.class,
                idEmpleado
        );

        if (ids.isEmpty()) {
            crearContrato(
                    idEmpleado,
                    idCargo,
                    tipoContrato,
                    fechaInicio,
                    salario,
                    activo
            );
            return;
        }

        jdbcTemplate.update("""
                UPDATE dbo.contratos
                SET
                    id_cargo = ?,
                    tipo_contrato = ?,
                    fecha_inicio = ?,
                    fecha_fin =
                        CASE WHEN ? = 1 THEN NULL
                             ELSE COALESCE(fecha_fin,CONVERT(date,GETDATE()))
                        END,
                    salario_mensual = ?,
                    estado =
                        CASE WHEN ? = 1 THEN N'Activo'
                             ELSE N'Finalizado'
                        END
                WHERE id_contrato = ?
                """,
                idCargo,
                tipoContrato,
                fechaInicio,
                activo ? 1 : 0,
                salario,
                activo ? 1 : 0,
                ids.get(0)
        );
    }


    private void cerrarHistorialVigente(Integer idEmpleado) {

        jdbcTemplate.update("""
                UPDATE dbo.historial_estado_empleado
                SET fecha_fin = CONVERT(date,GETDATE())
                WHERE id_empleado = ?
                  AND fecha_fin IS NULL
                """,
                idEmpleado
        );
    }


    private void registrarHistorialEstado(
            Integer idEmpleado,
            String estado,
            LocalDate fecha,
            String motivo) {

        jdbcTemplate.update("""
                INSERT INTO dbo.historial_estado_empleado (
                    id_empleado,
                    estado_empleado,
                    fecha_inicio,
                    fecha_fin,
                    motivo,
                    observaciones,
                    fecha_registro,
                    id_usuario_registro
                )
                VALUES (
                    ?, ?, ?, NULL,
                    ?,
                    N'Actualización automática desde Personal',
                    GETDATE(),
                    NULL
                )
                """,
                idEmpleado,
                estado,
                fecha,
                motivo
        );
    }


    private DatosEmpleado leerDatos(Map<String, Object> datos) {

        String sexo = texto(datos.get("sexo"));

        if ("Masculino".equalsIgnoreCase(sexo)) sexo = "M";
        if ("Femenino".equalsIgnoreCase(sexo)) sexo = "F";

        if (sexo != null &&
            !sexo.equals("M") &&
            !sexo.equals("F")) {

            sexo = null;
        }

        String email = texto(datos.get("email"));

        return new DatosEmpleado(
                mayusculas(texto(datos.get("codigoEmpleado"))),
                texto(datos.get("nombres")),
                texto(datos.get("apellidos")),
                texto(datos.get("cedula")),
                fecha(datos.get("fechaNacimiento")),
                sexo,
                telefono(datos.get("telefono")),
                email == null ? null : email.toLowerCase(),
                entero(datos.get("idCargo")),
                fecha(datos.get("fechaIngreso")),
                decimal(datos.get("salario")),
                texto(datos.get("tipoContrato")),
                texto(datos.get("turno")),
                !"Inactivo".equalsIgnoreCase(
                        texto(datos.get("estado"))
                ),
                texto(datos.get("direccion")) == null
                        ? "No registrada"
                        : texto(datos.get("direccion")),
                texto(datos.get("contactoEmergencia")),
                telefonoOpcional(datos.get("telefonoEmergencia")),
                texto(datos.get("numeroInss")),
                texto(datos.get("observaciones"))
        );
    }


    private Nombres separarNombres(String valor) {

        String limpio = valor.trim().replaceAll("\\s+", " ");
        String[] partes = limpio.split(" ", 2);

        return new Nombres(
                partes[0],
                partes.length > 1
                        ? partes[1]
                        : null
        );
    }


    private String generoTexto(String sexo) {
        if ("M".equals(sexo)) return "Masculino";
        if ("F".equals(sexo)) return "Femenino";
        return null;
    }


    private String telefono(Object valor) {

        String tel = texto(valor);

        if (tel == null) return null;

        String limpio = tel.replaceAll("[^0-9]", "");

        if (limpio.length() != 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El teléfono debe contener 8 dígitos."
            );
        }

        return limpio;
    }


    private String telefonoOpcional(Object valor) {

        String tel = texto(valor);

        if (tel == null) {
            return "00000000";
        }

        return telefono(tel);
    }


    private LocalDate fecha(Object valor) {

        String texto = texto(valor);

        if (texto == null) return null;

        try {
            return LocalDate.parse(texto);
        } catch (Exception error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hay una fecha con formato inválido."
            );
        }
    }


    private BigDecimal decimal(Object valor) {

        if (valor == null) return null;

        try {
            return new BigDecimal(String.valueOf(valor));
        } catch (NumberFormatException error) {
            return null;
        }
    }


    private Integer entero(Object valor) {

        if (valor == null) return null;

        try {
            return Integer.valueOf(String.valueOf(valor));
        } catch (NumberFormatException error) {
            return null;
        }
    }


    private String texto(Object valor) {

        if (valor == null) return null;

        String resultado =
                String.valueOf(valor).trim();

        return resultado.isEmpty()
                ? null
                : resultado;
    }


    private String mayusculas(String valor) {
        return valor == null
                ? null
                : valor.toUpperCase();
    }


    private boolean esActivo(Object valor) {

        if (valor == null) {
            return false;
        }

        if (valor instanceof Boolean booleano) {
            return booleano;
        }

        if (valor instanceof Number numero) {
            return numero.intValue() == 1;
        }

        String texto =
                String.valueOf(valor)
                .trim();

        return texto.equalsIgnoreCase("true")
                || texto.equals("1");
    }


    private Map<String, Object> respuestaOk(
            String mensaje,
            Integer idEmpleado) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("ok", true);
        respuesta.put("id_empleado", idEmpleado);
        respuesta.put("mensaje", mensaje);

        return respuesta;
    }


    private record Nombres(
            String primero,
            String segundo) {
    }


    private record DatosEmpleado(
            String codigoEmpleado,
            String nombres,
            String apellidos,
            String cedula,
            LocalDate fechaNacimiento,
            String sexo,
            String telefono,
            String email,
            Integer idCargo,
            LocalDate fechaIngreso,
            BigDecimal salario,
            String tipoContrato,
            String turno,
            boolean activo,
            String direccion,
            String contactoEmergencia,
            String telefonoEmergencia,
            String numeroInss,
            String observaciones) {
    }
}
