package com.gestionbovina.gestionbovina.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ganado")
@CrossOrigin(origins = "*")
public class GanadoController {

    private final JdbcTemplate jdbcTemplate;

    public GanadoController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /* =========================================================
       LISTAR GANADO
    ========================================================= */

    @GetMapping
    public List<Map<String, Object>> listarGanado() {

        String sql = """
                SELECT v.*,
                       CASE WHEN f.id_foto IS NULL THEN NULL
                            ELSE CONCAT('http://localhost:8080/api/ganado/', v.id_ganado, '/foto')
                       END AS foto_url
                FROM dbo.vw_ganado_control v
                OUTER APPLY (
                    SELECT TOP (1) id_foto
                    FROM dbo.fotos_ganado
                    WHERE id_ganado = v.id_ganado
                      AND activo = 1
                      AND es_principal = 1
                    ORDER BY id_foto DESC
                ) f
                ORDER BY v.id_ganado DESC
                """;

        return jdbcTemplate.queryForList(sql);
    }


    /* =========================================================
       FICHA INDIVIDUAL DEL BOVINO
       Reutiliza las vistas existentes; no modifica registros.
    ========================================================= */

    @GetMapping("/{id}/ficha")
    public Object obtenerFichaBovino(@PathVariable int id) {
        try {
            List<Map<String, Object>> bovinos = jdbcTemplate.queryForList("""
                    SELECT v.*,
                           CASE WHEN f.id_foto IS NULL THEN NULL
                                ELSE CONCAT('http://localhost:8080/api/ganado/', v.id_ganado, '/foto')
                           END AS foto_url
                    FROM dbo.vw_ganado_control v
                    OUTER APPLY (
                        SELECT TOP (1) id_foto
                        FROM dbo.fotos_ganado
                        WHERE id_ganado = v.id_ganado
                          AND activo = 1 AND es_principal = 1
                        ORDER BY id_foto DESC
                    ) f
                    WHERE v.id_ganado = ?
                    """, id);

            if (bovinos.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No se encontró el bovino solicitado.");
                return org.springframework.http.ResponseEntity.badRequest().body(error);
            }

            Map<String, Object> ficha = new HashMap<>();
            ficha.put("bovino", bovinos.get(0));

            // El historial se obtiene de la vista que ya utiliza Veterinaria.
            try {
                ficha.put("historialVeterinario", jdbcTemplate.queryForList(
                        "SELECT * FROM dbo.vw_veterinaria_control WHERE id_ganado = ? ORDER BY fecha_atencion DESC, id_registro DESC", id));
            } catch (DataAccessException noDisponible) {
                ficha.put("historialVeterinario", List.of());
                ficha.put("notaVeterinaria", "El historial veterinario no está disponible en la vista actual.");
            }

            return ficha;
        } catch (DataAccessException error) {
            return respuestaErrorSQL(error);
        }
    }


    /* =========================================================
       AGREGAR BOVINO
    ========================================================= */

    @PostMapping
    public Object agregarBovino(
            @RequestBody Map<String, Object> datos
    ) {

        try {

            DatosGanado datosGanado =
                    leerDatosGanado(datos);

            String sql = """
                    EXEC dbo.sp_Ganado_Insertar
                        @codigo_arete = ?,
                        @nombre_apodo = ?,
                        @fecha_nacimiento = ?,
                        @raza = ?,
                        @genero = ?,
                        @peso_actual = ?,
                        @observaciones = ?
                    """;

            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(
                            sql,
                            datosGanado.codigoArete,
                            datosGanado.nombreApodo,
                            datosGanado.fechaNacimiento,
                            datosGanado.raza,
                            datosGanado.genero,
                            datosGanado.pesoActual,
                            null
                    );

            if (resultado.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "mensaje",
                        "Bovino registrado correctamente."
                );

                return respuesta;
            }

            return resultado.get(0);

        }
        catch (DataAccessException error) {

            return respuestaErrorSQL(error);

        }
        catch (Exception error) {

            return respuestaDatosInvalidos();

        }
    }


    /* =========================================================
       MODIFICAR BOVINO
    ========================================================= */

    @PutMapping("/{id}")
    public Object modificarBovino(
            @PathVariable int id,
            @RequestBody Map<String, Object> datos
    ) {

        try {

            DatosGanado datosGanado =
                    leerDatosGanado(datos);

            List<Map<String, Object>> actual =
                    jdbcTemplate.queryForList(
                            """
                            SELECT
                                estado_salud,
                                observaciones
                            FROM dbo.ganado
                            WHERE id_ganado = ?
                            """,
                            id
                    );

            if (actual.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "error",
                        "El bovino indicado no existe."
                );

                return org.springframework.http.ResponseEntity
                        .badRequest()
                        .body(respuesta);
            }

            String estadoSalud =
                    String.valueOf(
                            actual.get(0)
                            .get("estado_salud")
                    );

            Object observacionesActuales =
                    actual.get(0)
                    .get("observaciones");

            String observaciones =
                    observacionesActuales == null
                            ? null
                            : String.valueOf(
                                    observacionesActuales
                            );

            String sql = """
                    EXEC dbo.sp_Ganado_Actualizar
                        @id_ganado = ?,
                        @codigo_arete = ?,
                        @nombre_apodo = ?,
                        @fecha_nacimiento = ?,
                        @raza = ?,
                        @genero = ?,
                        @peso_actual = ?,
                        @estado_salud = ?,
                        @observaciones = ?
                    """;

            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(
                            sql,
                            id,
                            datosGanado.codigoArete,
                            datosGanado.nombreApodo,
                            datosGanado.fechaNacimiento,
                            datosGanado.raza,
                            datosGanado.genero,
                            datosGanado.pesoActual,
                            estadoSalud,
                            observaciones
                    );

            if (resultado.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "mensaje",
                        "Bovino modificado correctamente."
                );

                return respuesta;
            }

            return resultado.get(0);

        }
        catch (DataAccessException error) {

            return respuestaErrorSQL(error);

        }
        catch (Exception error) {

            return respuestaDatosInvalidos();

        }
    }


    /* =========================================================
       DAR DE BAJA A UN BOVINO
    ========================================================= */

    @PatchMapping("/{id}/baja")
    public Object darDeBaja(
            @PathVariable int id,
            @RequestBody Map<String, Object> datos
    ) {

        try {

            Object motivoObj =
                    datos.get("motivo_baja");

            String motivo =
                    motivoObj == null
                            ? ""
                            : String.valueOf(
                                    motivoObj
                            ).trim();

            if (motivo.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "error",
                        "El motivo de la baja es obligatorio."
                );

                return org.springframework.http.ResponseEntity
                        .badRequest()
                        .body(respuesta);
            }

            List<Map<String, Object>> bovino =
                    jdbcTemplate.queryForList(
                            """
                            SELECT
                                id_ganado,
                                activo
                            FROM dbo.ganado
                            WHERE id_ganado = ?
                            """,
                            id
                    );

            if (bovino.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "error",
                        "El bovino indicado no existe."
                );

                return org.springframework.http.ResponseEntity
                        .badRequest()
                        .body(respuesta);
            }

            Object activoObj =
                    bovino.get(0).get("activo");

            boolean activo =
                    activoObj instanceof Boolean
                            ? (Boolean) activoObj
                            : Integer.parseInt(
                                    String.valueOf(activoObj)
                            ) == 1;

            if (!activo) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "error",
                        "Este bovino ya se encuentra dado de baja."
                );

                return org.springframework.http.ResponseEntity
                        .badRequest()
                        .body(respuesta);
            }

            String sql = """
                    EXEC dbo.sp_Ganado_DarBaja
                        @id_ganado = ?,
                        @motivo_baja = ?
                    """;

            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(
                            sql,
                            id,
                            motivo
                    );

            if (resultado.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "mensaje",
                        "Bovino dado de baja correctamente."
                );

                return respuesta;
            }

            return resultado.get(0);

        }
        catch (DataAccessException error) {

            return respuestaErrorSQL(error);

        }
        catch (Exception error) {

            Map<String, Object> respuesta =
                    new HashMap<>();

            respuesta.put(
                    "error",
                    "No se pudo dar de baja al bovino."
            );

            return org.springframework.http.ResponseEntity
                    .badRequest()
                    .body(respuesta);

        }
    }


    /* =========================================================
       LEER Y VALIDAR DATOS RECIBIDOS
    ========================================================= */

    private DatosGanado leerDatosGanado(
            Map<String, Object> datos
    ) {

        String codigoArete =
                String.valueOf(
                        datos.get("codigo_arete")
                ).trim();

        String nombreApodo =
                String.valueOf(
                        datos.get("nombre_apodo")
                ).trim();

        String fechaNacimientoTexto =
                String.valueOf(
                        datos.get("fecha_nacimiento")
                ).trim();

        String raza =
                String.valueOf(
                        datos.get("raza")
                ).trim();

        String genero =
                String.valueOf(
                        datos.get("genero")
                ).trim();

        double pesoActual =
                Double.parseDouble(
                        String.valueOf(
                                datos.get("peso_actual")
                        )
                );

        Date fechaNacimiento =
                Date.valueOf(
                        fechaNacimientoTexto
                );

        return new DatosGanado(
                codigoArete,
                nombreApodo,
                fechaNacimiento,
                raza,
                genero,
                pesoActual
        );
    }


    /* =========================================================
       RESPUESTAS DE ERROR
    ========================================================= */

    private Object respuestaErrorSQL(
            DataAccessException error
    ) {

        Map<String, Object> respuesta =
                new HashMap<>();

        respuesta.put(
                "error",
                obtenerMensajeSQL(error)
        );

        return org.springframework.http.ResponseEntity
                .badRequest()
                .body(respuesta);
    }


    private Object respuestaDatosInvalidos() {

        Map<String, Object> respuesta =
                new HashMap<>();

        respuesta.put(
                "error",
                "Los datos enviados no son válidos."
        );

        return org.springframework.http.ResponseEntity
                .badRequest()
                .body(respuesta);
    }


    private String obtenerMensajeSQL(
            DataAccessException error
    ) {

        Throwable causa =
                error.getMostSpecificCause();

        if (
                causa != null &&
                causa.getMessage() != null
        ) {

            String mensaje =
                    causa.getMessage();

            if (
                    mensaje.contains(
                            "Ya existe un bovino"
                    ) ||
                    mensaje.contains(
                            "Ya existe otro bovino"
                    )
            ) {

                return "Ya existe otro bovino con ese número de arete.";
            }

            if (
                    mensaje.contains(
                            "El bovino indicado no existe"
                    )
            ) {

                return "El bovino indicado no existe.";
            }

            if (
                    mensaje.contains(
                            "El número de arete es obligatorio"
                    )
            ) {

                return "El número de arete es obligatorio.";
            }

            if (
                    mensaje.contains(
                            "El nombre del bovino es obligatorio"
                    )
            ) {

                return "El nombre del bovino es obligatorio.";
            }

            if (
                    mensaje.contains(
                            "La raza es obligatoria"
                    )
            ) {

                return "La raza es obligatoria.";
            }

            if (
                    mensaje.contains(
                            "El género debe ser"
                    )
            ) {

                return "El sexo seleccionado no es válido.";
            }

            if (
                    mensaje.contains(
                            "El peso debe ser mayor que cero"
                    )
            ) {

                return "El peso debe ser mayor que cero.";
            }

        }

        return "No se pudo procesar la operación en la base de datos.";
    }


    /* =========================================================
       SUBIR / CONSULTAR FOTO PRINCIPAL DEL BOVINO
       Archivos almacenados fuera de SQL Server; la BD conserva la ruta.
    ========================================================= */

    private Path carpetaFotos() {
        return Paths.get(System.getProperty("user.dir"), "uploads", "ganado").toAbsolutePath().normalize();
    }

    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirFoto(@PathVariable int id, @RequestParam("foto") MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selecciona una fotografía."));
        }
        if (foto.getSize() > 5L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "La fotografía supera el límite de 5 MB."));
        }
        String tipo = foto.getContentType() == null ? "" : foto.getContentType().toLowerCase();
        String extension = switch (tipo) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> null;
        };
        if (extension == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato no permitido. Usa JPG, PNG o WEBP."));
        }
        try {
            Integer existe = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.ganado WHERE id_ganado = ?", Integer.class, id);
            if (existe == null || existe == 0) {
                return ResponseEntity.notFound().build();
            }
            Path carpeta = carpetaFotos();
            Files.createDirectories(carpeta);
            String nombre = "bovino_" + id + "_" + UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombre).normalize();
            if (!destino.startsWith(carpeta)) throw new IOException("Ruta de archivo no válida.");
            foto.transferTo(destino);
            String rutaRelativa = "uploads/ganado/" + nombre;
            try {
                jdbcTemplate.queryForList("EXEC dbo.sp_Subasta_RegistrarFotoGanado @id_ganado = ?, @ruta_foto = ?, @es_principal = ?, @descripcion = ?",
                        id, rutaRelativa, true, "Fotografía principal del bovino");
            } catch (Exception ex) {
                Files.deleteIfExists(destino);
                throw ex;
            }
            return ResponseEntity.ok(Map.of("mensaje", "Fotografía guardada correctamente.", "foto_url", "http://localhost:8080/api/ganado/" + id + "/foto"));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo guardar la fotografía: " + ex.getMessage()));
        }
    }

    @GetMapping(value = "/{id}/foto")
    public ResponseEntity<?> obtenerFoto(@PathVariable int id) {
        try {
            List<Map<String, Object>> filas = jdbcTemplate.queryForList("""
                    SELECT TOP (1) ruta_foto
                    FROM dbo.fotos_ganado
                    WHERE id_ganado = ? AND activo = 1 AND es_principal = 1
                    ORDER BY id_foto DESC
                    """, id);
            if (filas.isEmpty() || filas.get(0).get("ruta_foto") == null) return ResponseEntity.notFound().build();
            String ruta = String.valueOf(filas.get(0).get("ruta_foto")).replace('\\', '/');
            if (!ruta.startsWith("uploads/ganado/")) return ResponseEntity.badRequest().build();
            Path base = carpetaFotos();
            Path archivo = Paths.get(System.getProperty("user.dir"), ruta).toAbsolutePath().normalize();
            if (!archivo.startsWith(base) || !Files.isRegularFile(archivo)) return ResponseEntity.notFound().build();
            String nombre = archivo.getFileName().toString().toLowerCase();
            MediaType media = nombre.endsWith(".png") ? MediaType.IMAGE_PNG : nombre.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .contentType(media).body(new FileSystemResource(archivo));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo recuperar la fotografía."));
        }
    }

    /* =========================================================
       CLASE INTERNA PARA LOS DATOS DEL FORMULARIO
    ========================================================= */

    private static class DatosGanado {

        String codigoArete;
        String nombreApodo;
        Date fechaNacimiento;
        String raza;
        String genero;
        double pesoActual;

        DatosGanado(
                String codigoArete,
                String nombreApodo,
                Date fechaNacimiento,
                String raza,
                String genero,
                double pesoActual
        ) {

            this.codigoArete =
                    codigoArete;

            this.nombreApodo =
                    nombreApodo;

            this.fechaNacimiento =
                    fechaNacimiento;

            this.raza =
                    raza;

            this.genero =
                    genero;

            this.pesoActual =
                    pesoActual;
        }
    }
}
