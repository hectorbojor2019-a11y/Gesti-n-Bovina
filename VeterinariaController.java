package com.gestionbovina.gestionbovina.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/veterinaria")
@CrossOrigin(origins = "*")
public class VeterinariaController {

    private final JdbcTemplate jdbcTemplate;

    public VeterinariaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /* =========================================================
       LISTAR REGISTROS VETERINARIOS
    ========================================================= */

    @GetMapping
    public List<Map<String, Object>> listarRegistros() {

        String sql = """
                SELECT *
                FROM dbo.vw_veterinaria_control
                WHERE activo = 1
                ORDER BY
                    fecha_atencion DESC,
                    id_registro DESC
                """;

        return jdbcTemplate.queryForList(sql);
    }


    /* =========================================================
       FICHA CLINICA INDIVIDUAL POR BOVINO
       Utiliza exclusivamente los datos existentes en la vista SQL.
    ========================================================= */
    @GetMapping("/ficha/{idGanado}")
    public Map<String, Object> obtenerFichaClinica(@PathVariable int idGanado) {
        String sql = """
                SELECT *
                FROM dbo.vw_veterinaria_control
                WHERE id_ganado = ? AND activo = 1
                ORDER BY fecha_atencion DESC, id_registro DESC
                """;

        List<Map<String, Object>> historial = jdbcTemplate.queryForList(sql, idGanado);
        Map<String, Object> ficha = new HashMap<>();
        ficha.put("id_ganado", idGanado);
        ficha.put("historial", historial);
        ficha.put("total_atenciones", historial.size());
        if (!historial.isEmpty()) {
            Map<String, Object> ultimo = historial.get(0);
            ficha.put("codigo_arete", ultimo.get("codigo_arete"));
            ficha.put("nombre_apodo", ultimo.get("nombre_apodo"));
            ficha.put("sexo", ultimo.get("sexo"));
        }
        return ficha;
    }


    /* =========================================================
       LISTAR BOVINOS ACTIVOS
    ========================================================= */

    @GetMapping("/bovinos")
    public List<Map<String, Object>> listarBovinos() {

        String sql = """
                SELECT
                    id_ganado,
                    codigo_arete,
                    nombre_apodo,
                    estado_salud
                FROM dbo.ganado
                WHERE activo = 1
                ORDER BY
                    nombre_apodo,
                    codigo_arete
                """;

        return jdbcTemplate.queryForList(sql);
    }


    /* =========================================================
       ESTADÍSTICAS
    ========================================================= */

    @GetMapping("/estadisticas")
    public Map<String, Object> obtenerEstadisticas() {

        String sql = """
                SELECT
                    COUNT(*) AS total_atenciones,

                    COALESCE(
                        SUM(
                            CASE
                                WHEN tipo_atencion = N'Vacunación'
                                    THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS total_vacunaciones,

                    COALESCE(
                        SUM(
                            CASE
                                WHEN tipo_atencion = N'Tratamiento'
                                 AND estado = N'Pendiente'
                                    THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS tratamientos_activos,

                    COALESCE(
                        SUM(
                            CASE
                                WHEN estado = N'Pendiente'
                                    THEN 1
                                ELSE 0
                            END
                        ),
                        0
                    ) AS proximas_atenciones

                FROM dbo.revisiones_veterinarias
                WHERE activo = 1
                """;

        return jdbcTemplate.queryForMap(sql);
    }


    /* =========================================================
       AGREGAR ATENCIÓN VETERINARIA
    ========================================================= */

    @PostMapping
    public Object agregarAtencion(
            @RequestBody Map<String, Object> datos
    ) {

        try {

            int idGanado =
                    Integer.parseInt(
                            String.valueOf(
                                    datos.get("id_ganado")
                            )
                    );

            String tipoAtencion =
                    textoObligatorio(
                            datos,
                            "tipo_atencion"
                    );

            Date fechaAtencion =
                    Date.valueOf(
                            textoObligatorio(
                                    datos,
                                    "fecha_atencion"
                            )
                    );

            String nombreVeterinario =
                    textoObligatorio(
                            datos,
                            "nombre_veterinario"
                    );

            String diagnostico =
                    textoObligatorio(
                            datos,
                            "diagnostico"
                    );

            String medicamentoVacuna =
                    textoOpcional(
                            datos,
                            "medicamento_vacuna"
                    );

            String dosis =
                    textoOpcional(
                            datos,
                            "dosis"
                    );

            Date proximaAtencion =
                    fechaOpcional(
                            datos,
                            "proxima_atencion"
                    );

            String estado =
                    textoObligatorio(
                            datos,
                            "estado"
                    );

            String observaciones =
                    textoOpcional(
                            datos,
                            "observaciones"
                    );


            String sql = """
                    EXEC dbo.sp_Veterinaria_Insertar
                        @id_ganado = ?,
                        @tipo_atencion = ?,
                        @fecha_atencion = ?,
                        @nombre_veterinario = ?,
                        @diagnostico = ?,
                        @medicamento_vacuna = ?,
                        @dosis = ?,
                        @proxima_atencion = ?,
                        @estado = ?,
                        @observaciones = ?
                    """;


            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(
                            sql,
                            idGanado,
                            tipoAtencion,
                            fechaAtencion,
                            nombreVeterinario,
                            diagnostico,
                            medicamentoVacuna,
                            dosis,
                            proximaAtencion,
                            estado,
                            observaciones
                    );


            if (resultado.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "mensaje",
                        "Atención veterinaria registrada correctamente."
                );

                return respuesta;
            }


            return resultado.get(0);

        }
        catch (DataAccessException error) {

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
        catch (Exception error) {

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
    }


    /* =========================================================
       MODIFICAR ATENCIÓN VETERINARIA
    ========================================================= */

    @PutMapping("/{id}")
    public Object modificarAtencion(
            @PathVariable int id,
            @RequestBody Map<String, Object> datos
    ) {

        try {

            int idGanado =
                    Integer.parseInt(
                            String.valueOf(
                                    datos.get("id_ganado")
                            )
                    );

            String tipoAtencion =
                    textoObligatorio(
                            datos,
                            "tipo_atencion"
                    );

            Date fechaAtencion =
                    Date.valueOf(
                            textoObligatorio(
                                    datos,
                                    "fecha_atencion"
                            )
                    );

            String nombreVeterinario =
                    textoObligatorio(
                            datos,
                            "nombre_veterinario"
                    );

            String diagnostico =
                    textoObligatorio(
                            datos,
                            "diagnostico"
                    );

            String medicamentoVacuna =
                    textoOpcional(
                            datos,
                            "medicamento_vacuna"
                    );

            String dosis =
                    textoOpcional(
                            datos,
                            "dosis"
                    );

            Date proximaAtencion =
                    fechaOpcional(
                            datos,
                            "proxima_atencion"
                    );

            String estado =
                    textoObligatorio(
                            datos,
                            "estado"
                    );

            String observaciones =
                    textoOpcional(
                            datos,
                            "observaciones"
                    );


            String sql = """
                    EXEC dbo.sp_Veterinaria_Actualizar
                        @id_revision = ?,
                        @id_ganado = ?,
                        @tipo_atencion = ?,
                        @fecha_atencion = ?,
                        @nombre_veterinario = ?,
                        @diagnostico = ?,
                        @medicamento_vacuna = ?,
                        @dosis = ?,
                        @proxima_atencion = ?,
                        @estado = ?,
                        @observaciones = ?
                    """;


            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(
                            sql,
                            id,
                            idGanado,
                            tipoAtencion,
                            fechaAtencion,
                            nombreVeterinario,
                            diagnostico,
                            medicamentoVacuna,
                            dosis,
                            proximaAtencion,
                            estado,
                            observaciones
                    );


            if (resultado.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "mensaje",
                        "Registro veterinario modificado correctamente."
                );

                return respuesta;
            }


            return resultado.get(0);

        }
        catch (DataAccessException error) {

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
        catch (Exception error) {

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
    }


    /* =========================================================
       ELIMINAR REGISTRO VETERINARIO
       Eliminación lógica: el registro permanece en SQL Server.
    ========================================================= */

    @DeleteMapping("/{id}")
    public Object eliminarRegistro(
            @PathVariable int id
    ) {

        try {

            String sql = """
                    EXEC dbo.sp_Veterinaria_Eliminar
                        @id_revision = ?
                    """;


            List<Map<String, Object>> resultado =
                    jdbcTemplate.queryForList(
                            sql,
                            id
                    );


            if (resultado.isEmpty()) {

                Map<String, Object> respuesta =
                        new HashMap<>();

                respuesta.put(
                        "mensaje",
                        "Registro veterinario eliminado correctamente."
                );

                return respuesta;
            }


            return resultado.get(0);

        }
        catch (DataAccessException error) {

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
        catch (Exception error) {

            Map<String, Object> respuesta =
                    new HashMap<>();

            respuesta.put(
                    "error",
                    "No se pudo eliminar el registro veterinario."
            );

            return org.springframework.http.ResponseEntity
                    .badRequest()
                    .body(respuesta);
        }
    }


    /* =========================================================
       FUNCIONES AUXILIARES
    ========================================================= */

    private String textoObligatorio(
            Map<String, Object> datos,
            String campo
    ) {

        Object valor =
                datos.get(campo);

        if (valor == null) {
            throw new IllegalArgumentException();
        }

        String texto =
                String.valueOf(valor)
                .trim();

        if (texto.isEmpty()) {
            throw new IllegalArgumentException();
        }

        return texto;
    }


    private String textoOpcional(
            Map<String, Object> datos,
            String campo
    ) {

        Object valor =
                datos.get(campo);

        if (valor == null) {
            return null;
        }

        String texto =
                String.valueOf(valor)
                .trim();

        return texto.isEmpty()
                ? null
                : texto;
    }


    private Date fechaOpcional(
            Map<String, Object> datos,
            String campo
    ) {

        String texto =
                textoOpcional(
                        datos,
                        campo
                );

        return texto == null
                ? null
                : Date.valueOf(texto);
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
                        "El registro veterinario no existe o fue eliminado"
                )
                ||
                mensaje.contains(
                        "El registro veterinario no existe o ya fue eliminado"
                )
            ) {

                return "El registro veterinario no existe o ya fue eliminado.";
            }


            if (
                mensaje.contains(
                        "El bovino no existe o se encuentra dado de baja"
                )
            ) {

                return "El bovino seleccionado no existe o se encuentra dado de baja.";
            }


            if (
                mensaje.contains(
                        "El tipo de atención no es válido"
                )
            ) {

                return "El tipo de atención seleccionado no es válido.";
            }


            if (
                mensaje.contains(
                        "La fecha de atención es obligatoria"
                )
            ) {

                return "La fecha de atención es obligatoria.";
            }


            if (
                mensaje.contains(
                        "El nombre del veterinario es obligatorio"
                )
            ) {

                return "El nombre del veterinario es obligatorio.";
            }


            if (
                mensaje.contains(
                        "El diagnóstico es obligatorio"
                )
            ) {

                return "El diagnóstico es obligatorio.";
            }


            if (
                mensaje.contains(
                        "El estado indicado no es válido"
                )
            ) {

                return "El estado seleccionado no es válido.";
            }


            if (
                mensaje.contains(
                        "La próxima atención no puede ser anterior"
                )
            ) {

                return "La próxima atención no puede ser anterior a la fecha de atención.";
            }
        }


        return "No se pudo procesar la operación veterinaria en la base de datos.";
    }
}
