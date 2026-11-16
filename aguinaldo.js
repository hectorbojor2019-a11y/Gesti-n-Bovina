
/* ==========================================================
   GESTIÓN BOVINA - PLANILLA DE AGUINALDO
   Módulo independiente de planilla.js
   ========================================================== */

(() => {
    "use strict";

    const API_AGUINALDO =
        "http://localhost:8080/api/aguinaldo";

    if (document.getElementById("aguinaldoPanel")) return;

    /* ======================================================
       ICONOS SVG PROFESIONALES
       ====================================================== */

    const iconos = {
        planilla: `
            <svg viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="1.8"
                 stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">
                <rect x="3" y="4" width="18" height="16" rx="2"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
                <line x1="7" y1="15" x2="11" y2="15"/>
                <line x1="7" y1="17" x2="15" y2="17"/>
            </svg>
        `,

        calcular: `
            <svg viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">
                <rect x="4" y="2" width="16" height="20" rx="2"/>
                <line x1="8" y1="6" x2="16" y2="6"/>
                <path d="M8 11h.01M12 11h.01M16 11h.01
                         M8 15h.01M12 15h.01"/>
                <path d="M16 14v4M14 16h4"/>
            </svg>
        `,

        guardar: `
            <svg viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">
                <path d="M19 21H5a2 2 0 0 1-2-2V5
                         a2 2 0 0 1 2-2h11l5 5v11
                         a2 2 0 0 1-2 2z"/>
                <polyline points="17 21 17 13 7 13 7 21"/>
                <polyline points="7 3 7 8 15 8"/>
            </svg>
        `,

        imprimir: `
            <svg viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">
                <polyline points="6 9 6 2 18 2 18 9"/>
                <path d="M6 18H4a2 2 0 0 1-2-2v-5
                         a2 2 0 0 1 2-2h16
                         a2 2 0 0 1 2 2v5
                         a2 2 0 0 1-2 2h-2"/>
                <rect x="6" y="14" width="12" height="8"/>
            </svg>
        `,

        cerrar: `
            <svg viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">
                <path d="M18 6 6 18M6 6l12 12"/>
            </svg>
        `
    };

    /* ======================================================
       ESTILOS
       ====================================================== */

    const estilo = document.createElement("style");
    estilo.id = "aguinaldoStyles";

    estilo.textContent = `
        #aguinaldoPanel {
            display: none;
            background: #fff;
            border-radius: 14px;
            padding: 22px;
            margin: 18px 0;
            box-shadow: 0 2px 16px rgba(0,0,0,.06);
            color: #183d2d;
        }

        #aguinaldoPanel h2 {
            margin: 0 0 10px;
        }

        #aguinaldoPanel p {
            color: #65766d;
            font-size: 13px;
            line-height: 1.6;
        }

        #aguinaldoPanel .ag-actions {
            display: flex;
            gap: 9px;
            flex-wrap: wrap;
            align-items: center;
            margin: 18px 0;
        }

        #aguinaldoPanel .ag-actions label {
            font-weight: 700;
            font-size: 13px;
        }

        #aguinaldoPanel input {
            border: 1px solid #d8e1dc;
            border-radius: 8px;
            padding: 9px;
            margin-left: 5px;
            outline: none;
        }

        #aguinaldoPanel button {
            background: #146443;
            color: white;
            border: none;
            border-radius: 8px;
            padding: 10px 13px;
            cursor: pointer;
            font-weight: 700;
            transition: background-color .2s ease;
        }

        #aguinaldoPanel button:hover {
            background: #0f5337;
        }

        #aguinaldoPanel button:disabled {
            opacity: .4;
            cursor: not-allowed;
        }

        #aguinaldoPanel .ag-actions button {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
        }

        #aguinaldoPanel .ag-actions button svg {
            width: 17px;
            height: 17px;
            flex-shrink: 0;
        }

        #aguinaldoPanel .agPagar {
            background: #1f7553;
            padding: 7px 10px;
            font-size: 11px;
        }

        #aguinaldoPanel table {
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }

        #aguinaldoPanel th,
        #aguinaldoPanel td {
            border-bottom: 1px solid #e4ebe7;
            padding: 10px;
            text-align: left;
            white-space: nowrap;
        }

        #aguinaldoPanel th {
            color: #3d5046;
            font-size: 11px;
            text-transform: uppercase;
            background: #f6f9f7;
        }

        #aguinaldoPanel .ag-scroll {
            overflow-x: auto;
        }

        #aguinaldoPanel .ag-estado {
            padding: 10px 12px;
            background: #f6f9f7;
            border-radius: 8px;
            margin-bottom: 14px;
            font-size: 12px;
            line-height: 1.6;
        }

        #aguinaldoPanel .ag-total {
            margin-top: 18px;
            text-align: right;
            color: #153d2c;
        }

        #aguinaldoPanel .ag-pendiente,
        #aguinaldoPanel .ag-pagado,
        #aguinaldoPanel .ag-sin-guardar {
            display: inline-block;
            padding: 5px 9px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: 700;
        }

        #aguinaldoPanel .ag-pendiente {
            background: #fff6dd;
            color: #957000;
        }

        #aguinaldoPanel .ag-pagado {
            background: #e4f6eb;
            color: #287346;
        }

        #aguinaldoPanel .ag-sin-guardar {
            background: #eef1ef;
            color: #65736c;
        }

        #aguinaldoPanel .ag-advertencia {
            margin-top: 6px;
            color: #a25b00;
            font-size: 11px;
            line-height: 1.5;
            white-space: normal;
            max-width: 220px;
        }

        #aguinaldoPanel .ag-error {
            background: #fff0f0;
            color: #a32929;
        }

        #btnPlanillaAguinaldo .operation-icon svg {
            width: 25px;
            height: 25px;
        }

        @media(max-width: 800px) {
            #aguinaldoPanel {
                padding: 15px;
            }

            #aguinaldoPanel .ag-actions {
                flex-direction: column;
                align-items: stretch;
            }

            #aguinaldoPanel .ag-actions button {
                width: 100%;
            }
        }
    `;

    document.head.appendChild(estilo);

    /* ======================================================
       LOCALIZAR MÓDULO FINANZAS
       ====================================================== */

    const contenedor =
        document.querySelector(".operations");

    if (!contenedor) {
        console.error(
            "No se encontró el contenedor de Finanzas."
        );
        return;
    }

    /* ======================================================
       BOTÓN DE ACCESO
       ====================================================== */

    const boton = document.createElement("button");

    boton.type = "button";
    boton.className = "operation";
    boton.id = "btnPlanillaAguinaldo";

    boton.innerHTML = `
        <div class="operation-icon">
            ${iconos.planilla}
        </div>
        <strong>Planilla de aguinaldo</strong>
        <small>Calcular, guardar, pagar e imprimir</small>
    `;

    contenedor.appendChild(boton);

    /* ======================================================
       PANEL PRINCIPAL
       ====================================================== */

    const panel = document.createElement("section");
    panel.id = "aguinaldoPanel";

    panel.innerHTML = `
        <h2>Planilla de aguinaldo</h2>

        <p>
            Período anual del 1 de diciembre del año anterior
            al 30 de noviembre del año seleccionado.
            Los días trabajados se contabilizan desde
            la fecha de ingreso registrada en Personal.
        </p>

        <div class="ag-actions">

            <label>
                Año:
                <input
                    id="agAnio"
                    type="number"
                    min="2000"
                    max="2100"
                    style="width:90px"
                >
            </label>

            <button id="agCalcular" type="button">
                ${iconos.calcular}
                <span>Calcular</span>
            </button>

            <button id="agGuardar" type="button">
                ${iconos.guardar}
                <span>Guardar planilla</span>
            </button>

            <button id="agImprimir" type="button">
                ${iconos.imprimir}
                <span>Imprimir</span>
            </button>

            <button id="agCerrar" type="button">
                ${iconos.cerrar}
                <span>Cerrar</span>
            </button>

        </div>

        <p id="agEstado" class="ag-estado">
            Selecciona el año y presiona Calcular.
        </p>

        <div class="ag-scroll">
            <table>
                <thead>
                    <tr>
                        <th>Empleado</th>
                        <th>Cargo</th>
                        <th>Fecha ingreso</th>
                        <th>Salario base</th>
                        <th>Período</th>
                        <th>Tiempo trabajado</th>
                        <th>Aguinaldo calculado</th>
                        <th>Estado</th>
                        <th>Acciones</th>
                    </tr>
                </thead>

                <tbody id="agFilas">
                    <tr>
                        <td colspan="9"
                            style="text-align:center;padding:30px">
                            Todavía no se ha calculado
                            la planilla de aguinaldo.
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <h3 id="agTotal" class="ag-total">
            Total: C$0.00
        </h3>
    `;

    contenedor.insertAdjacentElement("afterend", panel);

    /* ======================================================
       VARIABLES
       ====================================================== */

    const $ = id => document.getElementById(id);

    let datosAguinaldo = null;
    let cargando = false;
    let ultimoDia = fechaLocal();

    $("agAnio").value = new Date().getFullYear();

    /* ======================================================
       FECHA ACTUAL DE NICARAGUA
       ====================================================== */

    function fechaLocal() {
        const partes = new Intl.DateTimeFormat(
            "en-CA",
            {
                timeZone: "America/Managua",
                year: "numeric",
                month: "2-digit",
                day: "2-digit"
            }
        ).formatToParts(new Date());

        const obtener = tipo =>
            partes.find(p => p.type === tipo)?.value;

        return (
            obtener("year") + "-" +
            obtener("month") + "-" +
            obtener("day")
        );
    }

    /* ======================================================
       FORMATO DE MONEDA
       ====================================================== */

    function dinero(valor) {
        const numero = Number(valor);

        if (!Number.isFinite(numero)) return "—";

        return new Intl.NumberFormat("es-NI", {
            style: "currency",
            currency: "NIO",
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(numero);
    }

    /* ======================================================
       SEGURIDAD PARA TEXTO HTML
       ====================================================== */

    function texto(valor) {
        return String(valor ?? "—").replace(
            /[&<>"']/g,
            c => ({
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': "&quot;",
                "'": "&#39;"
            })[c]
        );
    }

    /* ======================================================
       FORMATO DE FECHAS
       ====================================================== */

    function formatoFecha(valor) {
        if (!valor) return "—";

        const cadena = String(valor);
        const partes = cadena.match(
            /^(\d{4})-(\d{2})-(\d{2})/
        );

        if (!partes) return texto(cadena);

        return (
            partes[3] + "/" +
            partes[2] + "/" +
            partes[1]
        );
    }

    /* ======================================================
       VALIDAR AÑO
       ====================================================== */

    function obtenerAnio() {
        const anio = Number($("agAnio").value);

        if (
            !Number.isInteger(anio) ||
            anio < 2000 ||
            anio > 2100
        ) {
            throw new Error(
                "Selecciona un año válido."
            );
        }

        return anio;
    }

    /* ======================================================
       CONSULTAS A LA API
       ====================================================== */

    async function solicitud(url, metodo = "GET") {
        const respuesta = await fetch(url, {
            method: metodo,
            cache: "no-store",
            headers: {
                Accept: "application/json"
            }
        });

        const tipo =
            respuesta.headers.get("content-type") || "";

        let resultado;

        if (tipo.includes("application/json")) {
            resultado = await respuesta.json();
        } else {
            resultado = await respuesta.text();
        }

        if (!respuesta.ok) {
            const mensaje =
                typeof resultado === "object" && resultado
                    ? resultado.message ||
                      resultado.detail ||
                      resultado.error
                    : null;

            throw new Error(
                mensaje || `Error ${respuesta.status}`
            );
        }

        return resultado;
    }

    /* ======================================================
       ESTADO DE LOS EMPLEADOS
       ====================================================== */

    function estadoEmpleado(empleado) {
        if (empleado.pagado) {
            return `
                <span class="ag-pagado">
                    Pagado
                </span>
            `;
        }

        if (empleado.registrado) {
            return `
                <span class="ag-pendiente">
                    Pendiente
                </span>
            `;
        }

        return `
            <span class="ag-sin-guardar">
                Sin guardar
            </span>
        `;
    }

    /* ======================================================
       PERÍODO INDIVIDUAL
       ====================================================== */

    function periodoEmpleado(empleado) {
        if (
            !empleado.periodo_empleado_inicio ||
            !empleado.periodo_empleado_fin
        ) {
            return "—";
        }

        return (
            formatoFecha(empleado.periodo_empleado_inicio) +
            " al " +
            formatoFecha(empleado.periodo_empleado_fin)
        );
    }

    /* ======================================================
       DIFERENCIAS DE MONTOS GUARDADOS
       ====================================================== */

    function advertenciaMonto(empleado) {
        if (!empleado.monto_guardado_diferente) {
            return "";
        }

        return `
            <div class="ag-advertencia">
                El monto guardado es diferente.
                <br>
                Cálculo actual:
                <strong>
                    ${dinero(empleado.monto_estimado)}
                </strong>
                <br>
                Revisar antes de pagar.
            </div>
        `;
    }

    /* ======================================================
       MOSTRAR LA PLANILLA
       ====================================================== */

    function mostrar(datos) {
        if (!datos || !Array.isArray(datos.empleados)) {
            throw new Error(
                "El servidor no devolvió una planilla válida."
            );
        }

        datosAguinaldo = datos;

        $("agEstado").classList.remove("ag-error");

        $("agEstado").textContent =
            `Período anual: ` +
            `${formatoFecha(datos.periodo_inicio)} al ` +
            `${formatoFecha(datos.periodo_fin)}. ` +
            `Fecha de corte: ` +
            `${formatoFecha(
                datos.fecha_corte || datos.periodo_fin
            )}.`;

        if (datos.empleados.length === 0) {
            $("agFilas").innerHTML = `
                <tr>
                    <td colspan="9"
                        style="text-align:center;padding:30px">
                        No hay empleados disponibles.
                    </td>
                </tr>
            `;
        } else {
            $("agFilas").innerHTML =
                datos.empleados.map(empleado => {

                    const id = Number(empleado.id_empleado);

                    const puedePagar =
                        empleado.registrado &&
                        !empleado.pagado &&
                        !empleado.monto_guardado_diferente &&
                        Number.isSafeInteger(id) &&
                        id > 0;

                    return `
                        <tr>
                            <td>
                                ${texto(empleado.nombre_completo)}
                            </td>

                            <td>
                                ${texto(empleado.cargo)}
                            </td>

                            <td>
                                ${formatoFecha(
                                    empleado.fecha_ingreso
                                )}
                            </td>

                            <td>
                                ${dinero(
                                    empleado.salario_actual
                                )}
                            </td>

                            <td>
                                ${periodoEmpleado(empleado)}
                            </td>

                            <td>
                                <strong>
                                    ${texto(
                                        empleado.dias_trabajados
                                    )} días
                                </strong>
                            </td>

                            <td style="
                                font-weight:800;
                                color:#146443;
                            ">
                                ${dinero(
                                    empleado.monto_aguinaldo
                                )}

                                ${advertenciaMonto(empleado)}
                            </td>

                            <td>
                                ${estadoEmpleado(empleado)}
                            </td>

                            <td>
                                ${puedePagar ? `
                                    <button
                                        type="button"
                                        class="agPagar"
                                        data-id="${id}"
                                    >
                                        Marcar pagado
                                    </button>
                                ` : "—"}
                            </td>
                        </tr>
                    `;
                }).join("");
        }

        $("agTotal").textContent =
            "Total de aguinaldo: " +
            dinero(datos.total);
    }

    /* ======================================================
       CONTROL DE ERRORES
       ====================================================== */

    async function ejecutar(funcion) {
        if (cargando) return;

        cargando = true;

        $("agCalcular").disabled = true;
        $("agGuardar").disabled = true;
        $("agImprimir").disabled = true;

        try {
            await funcion();
        } catch (error) {
            console.error("Error en aguinaldo:", error);

            $("agEstado").classList.add("ag-error");
            $("agEstado").textContent =
                error.message || "Error desconocido.";

            alert(error.message || "Ocurrió un error.");
        } finally {
            cargando = false;

            $("agCalcular").disabled = false;
            $("agGuardar").disabled = false;
            $("agImprimir").disabled = false;
        }
    }

    /* ======================================================
       CALCULAR AGUINALDO
       ====================================================== */

    async function calcular() {
        const anio = obtenerAnio();

        await ejecutar(async () => {
            $("agEstado").classList.remove("ag-error");
            $("agEstado").textContent =
                "Actualizando planilla de aguinaldo...";

            const resultado = await solicitud(
                `${API_AGUINALDO}/${anio}`
            );

            mostrar(resultado);

            ultimoDia = fechaLocal();
        });
    }

    $("agCalcular").onclick = calcular;

    /* ======================================================
       ABRIR PANEL Y ACTUALIZAR
       ====================================================== */

    boton.onclick = () => {
        panel.style.display = "block";

        panel.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });

        calcular();
    };

    /* ======================================================
       CERRAR PANEL
       ====================================================== */

    $("agCerrar").onclick = () => {
        panel.style.display = "none";
    };

    /* ======================================================
       ACTUALIZACIÓN AUTOMÁTICA DIARIA
       ====================================================== */

    function actualizarSiCambioDia() {
        const diaActual = fechaLocal();

        if (diaActual !== ultimoDia) {
            ultimoDia = diaActual;

            if (
                panel.style.display === "block" &&
                !cargando
            ) {
                calcular();
            }
        }
    }

    setInterval(actualizarSiCambioDia, 60000);

    document.addEventListener(
        "visibilitychange",
        () => {
            if (!document.hidden) {
                actualizarSiCambioDia();
            }
        }
    );

    /* ======================================================
       GUARDAR PLANILLA
       ====================================================== */

    $("agGuardar").onclick = () => {
        ejecutar(async () => {
            const anio = obtenerAnio();

            if (
                !datosAguinaldo ||
                Number(datosAguinaldo.anio) !== anio
            ) {
                throw new Error(
                    "Primero calcula la planilla seleccionada."
                );
            }

            const confirmar = confirm(
                "¿Guardar los cálculos de aguinaldo " +
                "que todavía no estén registrados? " +
                "Los registros existentes no serán modificados."
            );

            if (!confirmar) return;

            $("agEstado").textContent =
                "Guardando planilla de aguinaldo...";

            const resultado = await solicitud(
                `${API_AGUINALDO}/${anio}/guardar`,
                "POST"
            );

            mostrar(resultado);

            alert(
                "Planilla guardada correctamente."
            );
        });
    };

    /* ======================================================
       MARCAR COMO PAGADO
       ====================================================== */

    $("agFilas").onclick = event => {
        const botonPago =
            event.target.closest(".agPagar");

        if (!botonPago) return;

        ejecutar(async () => {
            const idEmpleado =
                Number(botonPago.dataset.id);

            const anio = obtenerAnio();

            const empleado =
                datosAguinaldo?.empleados?.find(
                    e => Number(e.id_empleado) === idEmpleado
                );

            if (!empleado || !empleado.registrado) {
                throw new Error(
                    "No existe un registro pendiente válido."
                );
            }

            if (empleado.pagado) {
                throw new Error(
                    "Este aguinaldo ya fue pagado."
                );
            }

            if (empleado.monto_guardado_diferente) {
                throw new Error(
                    "El monto guardado no coincide con " +
                    "el cálculo actual. Revisa el registro."
                );
            }

            const confirmar = confirm(
                `¿Confirmar el pago de aguinaldo a ` +
                `${empleado.nombre_completo} por ` +
                `${dinero(empleado.monto_aguinaldo)}?`
            );

            if (!confirmar) return;

            botonPago.disabled = true;
            botonPago.textContent = "Registrando...";

            const resultado = await solicitud(
                `${API_AGUINALDO}/${anio}/pagar/${idEmpleado}`,
                "POST"
            );

            mostrar(resultado);

            alert(
                "Pago de aguinaldo registrado correctamente."
            );
        });
    };

    /* ======================================================
       IMPRIMIR PLANILLA
       ====================================================== */

    $("agImprimir").onclick = () => {
        if (!datosAguinaldo) {
            alert("Primero calcula la planilla.");
            return;
        }

        if (
            Number(datosAguinaldo.anio) !==
            Number($("agAnio").value)
        ) {
            alert(
                "El año cambió. Calcula nuevamente."
            );
            return;
        }

        const empleados =
            Array.isArray(datosAguinaldo.empleados)
                ? datosAguinaldo.empleados
                : [];

        const ventana = window.open("", "_blank");

        if (!ventana) {
            alert(
                "El navegador bloqueó la ventana de impresión."
            );
            return;
        }

        const filas = empleados.map(empleado => `
            <tr>
                <td>
                    ${texto(empleado.nombre_completo)}
                </td>

                <td>
                    ${texto(empleado.cargo)}
                </td>

                <td>
                    ${formatoFecha(empleado.fecha_ingreso)}
                </td>

                <td>
                    ${dinero(empleado.salario_actual)}
                </td>

                <td>
                    ${periodoEmpleado(empleado)}
                </td>

                <td>
                    ${texto(empleado.dias_trabajados)}
                    días
                </td>

                <td>
                    ${dinero(empleado.monto_aguinaldo)}
                    ${empleado.monto_guardado_diferente
                        ? " (Revisar diferencia)"
                        : ""}
                </td>

                <td>
                    ${empleado.pagado
                        ? "Pagado"
                        : empleado.registrado
                            ? "Pendiente"
                            : "Sin guardar"}
                </td>
            </tr>
        `).join("");

        ventana.document.write(`
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <title>Planilla de Aguinaldo</title>

                <style>
                    body {
                        font-family: Arial, sans-serif;
                        padding: 28px;
                        color: #1e3027;
                    }

                    h1 {
                        margin-bottom: 5px;
                    }

                    p {
                        color: #66756d;
                        line-height: 1.5;
                    }

                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 20px;
                    }

                    th, td {
                        border: 1px solid #d8dfdb;
                        padding: 9px;
                        text-align: left;
                        font-size: 12px;
                    }

                    th {
                        background: #f2f5f3;
                    }

                    .total {
                        margin-top: 22px;
                        text-align: right;
                        font-size: 18px;
                    }

                    @media print {
                        @page {
                            size: landscape;
                            margin: 12mm;
                        }
                    }
                </style>
            </head>

            <body>
                <h1>Gestión Bovina</h1>

                <h2>
                    Planilla de Aguinaldo
                    ${texto(datosAguinaldo.anio)}
                </h2>

                <p>
                    Período anual:
                    ${formatoFecha(
                        datosAguinaldo.periodo_inicio
                    )}
                    al
                    ${formatoFecha(
                        datosAguinaldo.periodo_fin
                    )}
                </p>

                <p>
                    Fecha de corte:
                    ${formatoFecha(
                        datosAguinaldo.fecha_corte ||
                        datosAguinaldo.periodo_fin
                    )}
                </p>

                <table>
                    <thead>
                        <tr>
                            <th>Empleado</th>
                            <th>Cargo</th>
                            <th>Fecha ingreso</th>
                            <th>Salario base</th>
                            <th>Período individual</th>
                            <th>Tiempo trabajado</th>
                            <th>Aguinaldo</th>
                            <th>Estado</th>
                        </tr>
                    </thead>

                    <tbody>
                        ${filas || `
                            <tr>
                                <td colspan="8"
                                    style="text-align:center">
                                    No hay empleados.
                                </td>
                            </tr>
                        `}
                    </tbody>
                </table>

                <div class="total">
                    <strong>Total:</strong>
                    ${dinero(datosAguinaldo.total)}
                </div>
            </body>
            </html>
        `);

        ventana.document.close();
        ventana.focus();

        setTimeout(() => {
            ventana.print();
        }, 250);
    };

})();
