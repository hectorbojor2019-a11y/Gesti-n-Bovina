package com.gestionbovina.gestionbovina.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /* =========================================================
       ESTADO DEL MÓDULO
       ========================================================= */

    @GetMapping("/estado")
    public Map<String, Object> estado() {

        Integer totalUsuarios =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.usuarios
                        """,
                        Integer.class
                );

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put("ok", true);
        respuesta.put("modulo", "USUARIOS");
        respuesta.put(
                "usuarios",
                totalUsuarios == null
                        ? 0
                        : totalUsuarios
        );

        return respuesta;
    }


    /* =========================================================
       LISTAR USUARIOS
       ========================================================= */

    @GetMapping
    public List<Map<String, Object>> listarUsuarios() {

        return jdbcTemplate.queryForList("""
                SELECT
                    u.id_usuario,
                    u.id_empleado,
                    e.codigo_empleado,

                    LTRIM(RTRIM(
                        CONCAT(
                            p.primer_nombre,
                            ' ',
                            ISNULL(p.segundo_nombre + ' ', ''),
                            p.primer_apellido,
                            ISNULL(' ' + p.segundo_apellido, '')
                        )
                    )) AS empleado,

                    u.nombre_usuario,

                    r.id_rol,
                    r.nombre_rol AS rol,

                    u.estado_cuenta,
                    u.requiere_cambio_clave,
                    u.fecha_creacion,
                    u.ultimo_acceso,
                    u.intentos_fallidos

                FROM dbo.usuarios u

                INNER JOIN dbo.empleados e
                    ON e.id_empleado = u.id_empleado

                INNER JOIN dbo.personas p
                    ON p.id_persona = e.id_persona

                INNER JOIN dbo.roles r
                    ON r.id_rol = u.id_rol

                WHERE COALESCE(e.es_tecnico, 0) = 0

                ORDER BY
                    CASE
                        WHEN u.estado_cuenta = 'Activo'
                            THEN 0
                        ELSE 1
                    END,
                    empleado
                """);
    }


    /* =========================================================
       EMPLEADOS DISPONIBLES PARA CREAR UNA CUENTA
       ========================================================= */

    @GetMapping("/empleados-disponibles")
    public List<Map<String, Object>>
    empleadosDisponibles() {

        return jdbcTemplate.queryForList("""
                SELECT
                    e.id_empleado,
                    e.codigo_empleado,

                    LTRIM(RTRIM(
                        CONCAT(
                            p.primer_nombre,
                            ' ',
                            ISNULL(p.segundo_nombre + ' ', ''),
                            p.primer_apellido,
                            ISNULL(' ' + p.segundo_apellido, '')
                        )
                    )) AS empleado,

                    c.nombre_cargo AS cargo,

                    CASE
                        WHEN e.Estado_actividad = 1
                            THEN 'Activo'
                        ELSE 'Inactivo'
                    END AS estado

                FROM dbo.empleados e

                INNER JOIN dbo.personas p
                    ON p.id_persona = e.id_persona

                INNER JOIN dbo.cargos c
                    ON c.id_cargo = e.id_cargo

                LEFT JOIN dbo.usuarios u
                    ON u.id_empleado = e.id_empleado

                WHERE
                    COALESCE(e.es_tecnico, 0) = 0

                    AND e.Estado_actividad = 1

                    AND u.id_usuario IS NULL

                ORDER BY empleado
                """);
    }


    /* =========================================================
       LISTAR ROLES
       ========================================================= */

    @GetMapping("/roles")
    public List<Map<String, Object>> listarRoles() {

        return jdbcTemplate.queryForList("""
                SELECT
                    id_rol,
                    nombre_rol,
                    descripcion,
                    nivel_acceso,
                    activo
                FROM dbo.roles
                WHERE activo = 1
                ORDER BY
                    CASE
                        WHEN nombre_rol = 'ADMIN'
                            THEN 0
                        ELSE 1
                    END,
                    nombre_rol
                """);
    }


    /* =========================================================
       CREAR USUARIO
       ========================================================= */

    @PostMapping
    @Transactional
    public Map<String, Object> crearUsuario(
            @RequestBody Map<String, Object> datos
    ) {

        Integer idEmpleado =
                convertirEntero(
                        datos.get("id_empleado")
                );

        Integer idRol =
                convertirEntero(
                        datos.get("id_rol")
                );

        String nombreUsuario =
                texto(
                        datos.get("nombre_usuario")
                )
                .toLowerCase();

        String contrasena =
                texto(
                        datos.get("contrasena")
                );


        /* -----------------------------
           VALIDACIONES
           ----------------------------- */

        if (idEmpleado == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes seleccionar un empleado."
            );
        }


        if (idRol == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes seleccionar un rol."
            );
        }


        validarNombreUsuario(
                nombreUsuario
        );


        validarContrasena(
                contrasena
        );


        /* -----------------------------
           COMPROBAR EMPLEADO
           ----------------------------- */

        Integer empleadoExiste =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.empleados
                        WHERE id_empleado = ?
                          AND Estado_actividad = 1
                          AND COALESCE(es_tecnico,0) = 0
                        """,
                        Integer.class,
                        idEmpleado
                );


        if (
                empleadoExiste == null ||
                empleadoExiste == 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El empleado no existe o se encuentra inactivo."
            );
        }


        /* -----------------------------
           EVITAR DOS CUENTAS
           PARA EL MISMO EMPLEADO
           ----------------------------- */

        Integer cuentaEmpleado =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.usuarios
                        WHERE id_empleado = ?
                        """,
                        Integer.class,
                        idEmpleado
                );


        if (
                cuentaEmpleado != null &&
                cuentaEmpleado > 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Este empleado ya tiene una cuenta de usuario."
            );
        }


        /* -----------------------------
           EVITAR USUARIO DUPLICADO
           ----------------------------- */

        Integer usuarioExiste =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.usuarios
                        WHERE LOWER(nombre_usuario)
                              = LOWER(?)
                        """,
                        Integer.class,
                        nombreUsuario
                );


        if (
                usuarioExiste != null &&
                usuarioExiste > 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El nombre de usuario ya está registrado."
            );
        }


        /* -----------------------------
           COMPROBAR ROL
           ----------------------------- */

        Integer rolExiste =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.roles
                        WHERE id_rol = ?
                          AND activo = 1
                        """,
                        Integer.class,
                        idRol
                );


        if (
                rolExiste == null ||
                rolExiste == 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El rol seleccionado no es válido."
            );
        }


        /* -----------------------------
           GENERAR SALT Y HASH
           ----------------------------- */

        String salt =
                generarSalt();

        String hash =
                generarHash(
                        contrasena,
                        salt
                );


        /* -----------------------------
           GUARDAR USUARIO
           ----------------------------- */

        try {

            jdbcTemplate.update("""
                    INSERT INTO dbo.usuarios
                    (
                        id_empleado,
                        nombre_usuario,
                        contrasena_hash,
                        contrasena_salt,
                        requiere_cambio_clave,
                        id_rol,
                        estado_cuenta,
                        fecha_creacion,
                        intentos_fallidos
                    )
                    VALUES
                    (
                        ?,
                        ?,
                        ?,
                        ?,
                        1,
                        ?,
                        'Activo',
                        GETDATE(),
                        0
                    )
                    """,
                    idEmpleado,
                    nombreUsuario,
                    hash,
                    salt,
                    idRol
            );

        }
        catch (
                DataIntegrityViolationException error
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se pudo crear el usuario porque existe información duplicada."
            );
        }


        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put("ok", true);

        respuesta.put(
                "mensaje",
                "Usuario creado correctamente."
        );

        respuesta.put(
                "nombre_usuario",
                nombreUsuario
        );

        return respuesta;
    }


    /* =========================================================
       CAMBIAR ROL
       ========================================================= */

    @PutMapping("/{idUsuario}/rol")
    public Map<String, Object> cambiarRol(
            @PathVariable Integer idUsuario,
            @RequestBody Map<String, Object> datos
    ) {

        Integer idRol =
                convertirEntero(
                        datos.get("id_rol")
                );


        if (idRol == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes seleccionar un rol."
            );
        }


        Integer existe =
                jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM dbo.roles
                        WHERE id_rol = ?
                          AND activo = 1
                        """,
                        Integer.class,
                        idRol
                );


        if (
                existe == null ||
                existe == 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El rol seleccionado no existe."
            );
        }


        int filas =
                jdbcTemplate.update("""
                        UPDATE dbo.usuarios
                        SET id_rol = ?
                        WHERE id_usuario = ?
                        """,
                        idRol,
                        idUsuario
                );


        if (filas == 0) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado."
            );
        }


        return respuestaCorrecta(
                "Rol actualizado correctamente."
        );
    }


    /* =========================================================
       CAMBIAR ESTADO DE CUENTA
       ========================================================= */

    @PutMapping("/{idUsuario}/estado")
    public Map<String, Object> cambiarEstado(
            @PathVariable Integer idUsuario,
            @RequestBody Map<String, Object> datos
    ) {

        String estado =
                texto(
                        datos.get("estado")
                );


        if (
                !estado.equals("Activo") &&
                !estado.equals("Inactivo") &&
                !estado.equals("Bloqueado") &&
                !estado.equals("Suspendido")
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El estado de la cuenta no es válido."
            );
        }


        int filas =
                jdbcTemplate.update("""
                        UPDATE dbo.usuarios

                        SET
                            estado_cuenta = ?,

                            intentos_fallidos =
                                CASE
                                    WHEN ? = 'Activo'
                                        THEN 0
                                    ELSE intentos_fallidos
                                END

                        WHERE id_usuario = ?
                        """,
                        estado,
                        estado,
                        idUsuario
                );


        if (filas == 0) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado."
            );
        }


        return respuestaCorrecta(
                "Estado de la cuenta actualizado correctamente."
        );
    }


    /* =========================================================
       RESTABLECER CONTRASEÑA
       ========================================================= */

    @PutMapping("/{idUsuario}/contrasena")
    @Transactional
    public Map<String, Object> restablecerContrasena(
            @PathVariable Integer idUsuario,
            @RequestBody Map<String, Object> datos
    ) {

        String nuevaContrasena =
                texto(
                        datos.get("contrasena")
                );


        validarContrasena(
                nuevaContrasena
        );


        List<Map<String, Object>> usuario =
                jdbcTemplate.queryForList("""
                        SELECT
                            contrasena_hash,
                            contrasena_salt
                        FROM dbo.usuarios
                        WHERE id_usuario = ?
                        """,
                        idUsuario
                );


        if (usuario.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado."
            );
        }


        Map<String, Object> actual =
                usuario.get(0);


        /*
         * Guardamos la contraseña anterior
         * en el historial.
         */

        jdbcTemplate.update("""
                INSERT INTO dbo.historial_contrasenas
                (
                    id_usuario,
                    contrasena_hash,
                    contrasena_salt,
                    fecha_cambio
                )
                VALUES
                (
                    ?,
                    ?,
                    ?,
                    GETDATE()
                )
                """,
                idUsuario,
                actual.get("contrasena_hash"),
                actual.get("contrasena_salt")
        );


        String nuevoSalt =
                generarSalt();

        String nuevoHash =
                generarHash(
                        nuevaContrasena,
                        nuevoSalt
                );


        jdbcTemplate.update("""
                UPDATE dbo.usuarios

                SET
                    contrasena_hash = ?,
                    contrasena_salt = ?,
                    requiere_cambio_clave = 1,
                    intentos_fallidos = 0

                WHERE id_usuario = ?
                """,
                nuevoHash,
                nuevoSalt,
                idUsuario
        );


        return respuestaCorrecta(
                "Contraseña restablecida correctamente."
        );
    }



    /* =========================================================
       INICIAR SESIÓN
       SQL SERVER + COMPATIBILIDAD CON USUARIOS ANTERIORES
       ========================================================= */

    @PostMapping("/login")
    @Transactional
    public Map<String, Object> iniciarSesion(
            @RequestBody Map<String, Object> datos
    ) {

        String nombreUsuario =
                texto(
                        datos.get("nombre_usuario")
                )
                .toLowerCase();

        String contrasena =
                texto(
                        datos.get("contrasena")
                );


        if (
                nombreUsuario.isBlank() ||
                contrasena.isBlank()
        ) {

            return respuestaLoginIncorrecto(
                    "Debes ingresar tu usuario y contraseña."
            );
        }


        List<Map<String, Object>> usuarios =
                jdbcTemplate.queryForList("""
                        SELECT TOP 1
                            u.id_usuario,
                            u.id_empleado,
                            u.nombre_usuario,
                            u.contrasena_hash,
                            u.contrasena_salt,
                            u.requiere_cambio_clave,
                            u.estado_cuenta,
                            u.intentos_fallidos,

                            r.nombre_rol AS rol,

                            LTRIM(RTRIM(
                                CONCAT(
                                    p.primer_nombre,
                                    ' ',
                                    ISNULL(p.segundo_nombre + ' ', ''),
                                    p.primer_apellido,
                                    ISNULL(' ' + p.segundo_apellido, '')
                                )
                            )) AS empleado

                        FROM dbo.usuarios u

                        INNER JOIN dbo.roles r
                            ON r.id_rol = u.id_rol

                        INNER JOIN dbo.empleados e
                            ON e.id_empleado = u.id_empleado

                        INNER JOIN dbo.personas p
                            ON p.id_persona = e.id_persona

                        WHERE LOWER(u.nombre_usuario)
                              = LOWER(?)
                        """,
                        nombreUsuario
                );


        /*
         * Si no existe en SQL Server, mantenemos
         * compatibilidad con los usuarios de demostración
         * que ya tenía el proyecto.
         *
         * Esto permite que:
         * admin
         * user_ganaderia
         * user_veterinaria
         * user_inventario
         * user_personal
         * user_finanzas
         *
         * continúen funcionando sin crear empleados ficticios
         * en la base de datos.
         */
        if (usuarios.isEmpty()) {

            Map<String, Object> demo =
                    autenticarUsuarioDemo(
                            nombreUsuario,
                            contrasena
                    );


            if (demo != null) {

                return demo;
            }


            return respuestaLoginIncorrecto(
                    "Usuario o contraseña incorrectos."
            );
        }


        Map<String, Object> usuario =
                usuarios.get(0);


        Integer idUsuario =
                convertirEntero(
                        usuario.get("id_usuario")
                );


        String estadoCuenta =
                texto(
                        usuario.get("estado_cuenta")
                );


        if (
                !estadoCuenta.equalsIgnoreCase(
                        "Activo"
                )
        ) {

            return respuestaLoginIncorrecto(
                    "La cuenta se encuentra " +
                    estadoCuenta.toLowerCase() +
                    ". Solicita ayuda al administrador."
            );
        }


        String hashGuardado =
                texto(
                        usuario.get("contrasena_hash")
                );

        String saltGuardado =
                texto(
                        usuario.get("contrasena_salt")
                );


        String hashIngresado =
                generarHash(
                        contrasena,
                        saltGuardado
                );


        boolean contrasenaCorrecta =
                MessageDigest.isEqual(
                        hashGuardado.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        hashIngresado.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        /* =====================================================
           CONTRASEÑA INCORRECTA
           ===================================================== */

        if (!contrasenaCorrecta) {

            Number intentosActuales =
                    (Number)
                    usuario.get(
                            "intentos_fallidos"
                    );


            int intentos =
                    (
                            intentosActuales == null
                                    ? 0
                                    : intentosActuales.intValue()
                    )
                    + 1;


            if (intentos >= 5) {

                jdbcTemplate.update("""
                        UPDATE dbo.usuarios
                        SET
                            intentos_fallidos = 5,
                            estado_cuenta = 'Bloqueado'
                        WHERE id_usuario = ?
                        """,
                        idUsuario
                );


                registrarLogAcceso(
                        idUsuario,
                        "Bloqueo",
                        "Cuenta bloqueada por cinco intentos fallidos."
                );


                return respuestaLoginIncorrecto(
                        "La cuenta fue bloqueada por varios intentos fallidos. Solicita al administrador que la active nuevamente."
                );
            }


            jdbcTemplate.update("""
                    UPDATE dbo.usuarios
                    SET intentos_fallidos = ?
                    WHERE id_usuario = ?
                    """,
                    intentos,
                    idUsuario
            );


            registrarLogAcceso(
                    idUsuario,
                    "Fallo",
                    "Contraseña incorrecta."
            );


            return respuestaLoginIncorrecto(
                    "Usuario o contraseña incorrectos."
            );
        }


        /* =====================================================
           LOGIN CORRECTO
           ===================================================== */

        jdbcTemplate.update("""
                UPDATE dbo.usuarios
                SET
                    intentos_fallidos = 0,
                    ultimo_acceso = GETDATE()
                WHERE id_usuario = ?
                """,
                idUsuario
        );


        registrarLogAcceso(
                idUsuario,
                "Login",
                "Inicio de sesión correcto."
        );


        Map<String, Object> datosUsuario =
                new LinkedHashMap<>();


        datosUsuario.put(
                "id_usuario",
                usuario.get("id_usuario")
        );

        datosUsuario.put(
                "id_empleado",
                usuario.get("id_empleado")
        );

        datosUsuario.put(
                "username",
                usuario.get("nombre_usuario")
        );

        datosUsuario.put(
                "nombre",
                usuario.get("empleado")
        );

        datosUsuario.put(
                "rol",
                texto(
                        usuario.get("rol")
                )
                .toUpperCase()
        );

        datosUsuario.put(
                "requiere_cambio_clave",
                usuario.get(
                        "requiere_cambio_clave"
                )
        );

        datosUsuario.put(
                "origen",
                "BD"
        );


        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "correcto",
                true
        );

        respuesta.put(
                "mensaje",
                "Inicio de sesión correcto."
        );

        respuesta.put(
                "usuario",
                datosUsuario
        );


        return respuesta;
    }


    /* =========================================================
       USUARIOS ANTERIORES DEL PROYECTO
       ========================================================= */

    private Map<String, Object> autenticarUsuarioDemo(
            String nombreUsuario,
            String contrasena
    ) {

        String passwordCorrecta;
        String nombre;
        String rol;
        Integer id;


        switch (nombreUsuario) {

            case "admin" -> {
                id = 1;
                passwordCorrecta = "Admin123";
                nombre = "Administrador";
                rol = "ADMIN";
            }

            case "user_ganaderia" -> {
                id = 2;
                passwordCorrecta = "Ganado123";
                nombre = "Usuario Ganadería";
                rol = "GANADERIA";
            }

            case "user_veterinaria" -> {
                id = 3;
                passwordCorrecta = "Veterinaria123";
                nombre = "Usuario Veterinaria";
                rol = "VETERINARIA";
            }

            case "user_inventario" -> {
                id = 4;
                passwordCorrecta = "Inventario123";
                nombre = "Usuario Inventario";
                rol = "INVENTARIO";
            }

            case "user_personal" -> {
                id = 5;
                passwordCorrecta = "Personal123";
                nombre = "Usuario Personal";
                rol = "PERSONAL";
            }

            case "user_finanzas" -> {
                id = 6;
                passwordCorrecta = "Finanzas123";
                nombre = "Usuario Finanzas";
                rol = "FINANZAS";
            }

            default -> {
                return null;
            }
        }


        if (
                !MessageDigest.isEqual(
                        passwordCorrecta.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        contrasena.getBytes(
                                StandardCharsets.UTF_8
                        )
                )
        ) {

            return null;
        }


        Map<String, Object> usuario =
                new LinkedHashMap<>();

        usuario.put(
                "id_usuario",
                id
        );

        usuario.put(
                "id_empleado",
                null
        );

        usuario.put(
                "username",
                nombreUsuario
        );

        usuario.put(
                "nombre",
                nombre
        );

        usuario.put(
                "rol",
                rol
        );

        usuario.put(
                "requiere_cambio_clave",
                false
        );

        usuario.put(
                "origen",
                "COMPATIBILIDAD"
        );


        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "correcto",
                true
        );

        respuesta.put(
                "mensaje",
                "Inicio de sesión correcto."
        );

        respuesta.put(
                "usuario",
                usuario
        );


        return respuesta;
    }


    /* =========================================================
       REGISTRAR ACCESO
       ========================================================= */

    private void registrarLogAcceso(
            Integer idUsuario,
            String tipoEvento,
            String descripcion
    ) {

        if (idUsuario == null) {

            return;
        }


        try {

            jdbcTemplate.update("""
                    INSERT INTO dbo.log_accesos
                    (
                        id_usuario,
                        fecha_hora,
                        tipo_evento,
                        descripcion
                    )
                    VALUES
                    (
                        ?,
                        GETDATE(),
                        ?,
                        ?
                    )
                    """,
                    idUsuario,
                    tipoEvento,
                    descripcion
            );

        }
        catch (Exception error) {

            /*
             * El registro del log no debe impedir
             * que el usuario pueda iniciar sesión.
             */
            System.err.println(
                    "No se pudo registrar log de acceso: "
                    + error.getMessage()
            );
        }
    }


    /* =========================================================
       RESPUESTA DE LOGIN INCORRECTO
       ========================================================= */

    private Map<String, Object> respuestaLoginIncorrecto(
            String mensaje
    ) {

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "correcto",
                false
        );

        respuesta.put(
                "mensaje",
                mensaje
        );

        return respuesta;
    }


    /* =========================================================
       MÉTODOS AUXILIARES
       ========================================================= */

    private String texto(
            Object valor
    ) {

        if (valor == null) {
            return "";
        }

        return String.valueOf(valor)
                .trim();
    }


    private Integer convertirEntero(
            Object valor
    ) {

        if (valor == null) {
            return null;
        }

        try {

            return Integer.valueOf(
                    String.valueOf(valor)
            );

        }
        catch (NumberFormatException error) {

            return null;
        }
    }


    private void validarNombreUsuario(
            String usuario
    ) {

        if (
                usuario == null ||
                usuario.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre de usuario es obligatorio."
            );
        }


        if (usuario.length() < 4) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre de usuario debe tener al menos 4 caracteres."
            );
        }


        if (usuario.length() > 50) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El nombre de usuario no puede superar los 50 caracteres."
            );
        }


        if (
                !usuario.matches(
                        "^[a-zA-Z0-9._-]+$"
                )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El usuario solo puede contener letras, números, punto, guion y guion bajo."
            );
        }
    }


    private void validarContrasena(
            String contrasena
    ) {

        if (
                contrasena == null ||
                contrasena.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña es obligatoria."
            );
        }


        if (contrasena.length() < 8) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos 8 caracteres."
            );
        }


        if (contrasena.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La contraseña es demasiado larga."
            );
        }
    }


    /* =========================================================
       GENERAR SALT
       ========================================================= */

    private String generarSalt() {

        byte[] salt =
                new byte[16];

        SecureRandom secureRandom =
                new SecureRandom();

        secureRandom.nextBytes(
                salt
        );

        return Base64
                .getEncoder()
                .encodeToString(
                        salt
                );
    }


    /* =========================================================
       HASH DE CONTRASEÑA
       PBKDF2 + SHA-256
       ========================================================= */

    private String generarHash(
            String contrasena,
            String saltBase64
    ) {

        try {

            byte[] salt =
                    Base64
                    .getDecoder()
                    .decode(
                            saltBase64
                    );


            PBEKeySpec especificacion =
                    new PBEKeySpec(
                            contrasena.toCharArray(),
                            salt,
                            120000,
                            256
                    );


            SecretKeyFactory fabrica =
                    SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA256"
                    );


            byte[] hash =
                    fabrica
                    .generateSecret(
                            especificacion
                    )
                    .getEncoded();


            especificacion.clearPassword();


            return Base64
                    .getEncoder()
                    .encodeToString(
                            hash
                    );

        }
        catch (Exception error) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo proteger la contraseña."
            );
        }
    }


    private Map<String, Object>
    respuestaCorrecta(
            String mensaje
    ) {

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put(
                "ok",
                true
        );

        respuesta.put(
                "mensaje",
                mensaje
        );

        return respuesta;
    }
}
