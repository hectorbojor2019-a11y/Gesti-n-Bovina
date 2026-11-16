/* ============================================================
   GESTIÓN BOVINA - PLANILLA CONECTADA A SQL SERVER
   ------------------------------------------------------------
   Este archivo se carga DESPUÉS del script actual de Finanzas.html.
   Reemplaza la antigua lógica de localStorage sin tocar
   Operaciones comerciales.
   ============================================================ */

const API_PLANILLA = "http://localhost:8080/api/planilla";

let planillaBD = null;


/* ============================================================
   UTILIDADES
   ============================================================ */

function planillaMoneda(valor) {

    return new Intl.NumberFormat(
        "es-NI",
        {
            style: "currency",
            currency: "NIO",
            minimumFractionDigits: 2
        }
    ).format(
        Number(valor) || 0
    );
}


function planillaTexto(valor) {

    if (
        valor === null ||
        valor === undefined ||
        valor === ""
    ) {
        return "—";
    }

    return String(valor)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}


function planillaIniciales(nombre) {

    const partes = String(nombre || "")
        .trim()
        .split(/\s+/)
        .filter(Boolean);

    if (partes.length === 0) {
        return "E";
    }

    if (partes.length === 1) {
        return partes[0]
            .substring(0, 2)
            .toUpperCase();
    }

    return (
        partes[0][0] +
        partes[1][0]
    ).toUpperCase();
}


async function planillaLeerRespuesta(response) {

    const texto = await response.text();

    if (!texto) {
        return {};
    }

    try {

        return JSON.parse(texto);

    } catch (_) {

        return {
            mensaje: texto
        };
    }
}


function planillaMensajeError(
    resultado,
    defecto
) {

    return (
        resultado.mensaje ||
        resultado.detail ||
        resultado.message ||
        resultado.error ||
        defecto
    );
}


function planillaEsPendiente() {

    return (
        planillaBD &&
        String(
            planillaBD.estado || ""
        ).toLowerCase() === "pendiente"
    );
}


function planillaDetalleActivo(detalle) {

    return String(
        detalle.estado_detalle || ""
    ).toUpperCase() === "ACTIVO";
}


/* ============================================================
   ESTILOS Y MODAL AUXILIAR
   ============================================================ */

function instalarInterfazPlanilla() {

    if (
        !document.getElementById(
            "planillaBDStyles"
        )
    ) {

        const style =
            document.createElement(
                "style"
            );

        style.id =
            "planillaBDStyles";

        style.textContent = `

            .payroll-actions{
                display:flex;
                gap:6px;
                flex-wrap:wrap;
            }

            .payroll-action{
                border:none;
                border-radius:8px;
                padding:7px 9px;
                cursor:pointer;
                font-weight:700;
                background:#eef5f1;
                color:#164b36;
            }

            .payroll-action:hover{
                background:#e0eee6;
            }

            .payroll-action.danger{
                background:#fff0f0;
                color:#b34040;
            }

            .payroll-action.success{
                background:#eaf7ef;
                color:#34764b;
            }

            .payroll-state{
                display:inline-block;
                padding:6px 9px;
                border-radius:999px;
                font-size:10px;
                font-weight:800;
            }

            .payroll-state.active{
                background:#eaf7ef;
                color:#34764b;
            }

            .payroll-state.cancelled{
                background:#fff0f0;
                color:#b34040;
            }

            .payroll-db-modal{
                position:fixed;
                inset:0;
                background:rgba(9,24,17,.58);
                display:none;
                align-items:center;
                justify-content:center;
                z-index:9999;
                padding:20px;
            }

            .payroll-db-modal.show{
                display:flex;
            }

            .payroll-db-box{
                width:min(680px,100%);
                max-height:90vh;
                overflow:auto;
                background:white;
                border-radius:18px;
                padding:24px;
                box-shadow:0 25px 70px rgba(0,0,0,.22);
            }

            .payroll-db-head{
                display:flex;
                justify-content:space-between;
                gap:20px;
                align-items:flex-start;
                margin-bottom:18px;
            }

            .payroll-db-head h3{
                margin:0 0 5px;
                font-size:22px;
            }

            .payroll-db-head p{
                margin:0;
                color:#7c8982;
                font-size:12px;
            }

            .payroll-db-close{
                width:38px;
                height:38px;
                border:none;
                border-radius:10px;
                cursor:pointer;
                font-size:18px;
            }

            .payroll-db-grid{
                display:grid;
                grid-template-columns:1fr 1fr;
                gap:13px;
            }

            .payroll-db-field{
                display:flex;
                flex-direction:column;
                gap:6px;
            }

            .payroll-db-field.full{
                grid-column:1/-1;
            }

            .payroll-db-field label{
                font-size:11px;
                font-weight:800;
                color:#34483e;
            }

            .payroll-db-field input,
            .payroll-db-field select,
            .payroll-db-field textarea{
                border:1px solid #d9e3de;
                border-radius:10px;
                padding:11px 12px;
                font-family:inherit;
                outline:none;
            }

            .payroll-db-field textarea{
                min-height:78px;
                resize:vertical;
            }

            .payroll-db-footer{
                display:flex;
                justify-content:flex-end;
                gap:10px;
                margin-top:20px;
            }

            .payroll-btn-secondary{
                border:1px solid #d9e3de;
                background:white;
                padding:10px 15px;
                border-radius:9px;
                cursor:pointer;
                font-weight:700;
            }

            .payroll-btn-primary{
                border:none;
                background:#0d4c35;
                color:white;
                padding:10px 15px;
                border-radius:9px;
                cursor:pointer;
                font-weight:800;
            }

            .payroll-btn-danger{
                border:none;
                background:#b74646;
                color:white;
                padding:10px 15px;
                border-radius:9px;
                cursor:pointer;
                font-weight:800;
            }

            @media(max-width:700px){

                .payroll-db-grid{
                    grid-template-columns:1fr;
                }

                .payroll-db-field.full{
                    grid-column:auto;
                }
            }
        `;

        document.head.appendChild(
            style
        );
    }


    if (
        !document.getElementById(
            "planillaDBModal"
        )
    ) {

        const modal =
            document.createElement(
                "div"
            );

        modal.className =
            "payroll-db-modal";

        modal.id =
            "planillaDBModal";

        modal.innerHTML = `

            <div class="payroll-db-box">

                <div class="payroll-db-head">

                    <div>

                        <h3 id="planillaDBTitulo">
                            Gestión de planilla
                        </h3>

                        <p id="planillaDBSubtitulo">
                            Información conectada con SQL Server.
                        </p>

                    </div>


                    <button
                        class="payroll-db-close"
                        onclick="cerrarModalPlanillaBD()"
                    >
                        ×
                    </button>

                </div>


                <div id="planillaDBContenido"></div>

            </div>
        `;

        document.body.appendChild(
            modal
        );


        modal.addEventListener(
            "click",
            function(event) {

                if (
                    event.target === modal
                ) {

                    cerrarModalPlanillaBD();
                }
            }
        );
    }


    instalarBotonesPlanilla();
}


function abrirModalPlanillaBD(
    titulo,
    subtitulo,
    contenido
) {

    document.getElementById(
        "planillaDBTitulo"
    ).textContent =
        titulo;


    document.getElementById(
        "planillaDBSubtitulo"
    ).textContent =
        subtitulo;


    document.getElementById(
        "planillaDBContenido"
    ).innerHTML =
        contenido;


    document.getElementById(
        "planillaDBModal"
    ).classList.add(
        "show"
    );
}


function cerrarModalPlanillaBD() {

    document.getElementById(
        "planillaDBModal"
    ).classList.remove(
        "show"
    );
}


/* ============================================================
   BOTONES DE OPERACIONES
   ============================================================ */

function instalarBotonesPlanilla() {

    const contenedor =
        document.querySelector(
            ".operations"
        );

    if (!contenedor) {
        return;
    }


    const botones =
        Array.from(
            contenedor.querySelectorAll(
                "button"
            )
        );


    const tieneAgregar =
        botones.some(
            boton =>
                boton.textContent
                    .toLowerCase()
                    .includes(
                        "agregar a planilla"
                    )
        );


    if (!tieneAgregar) {

        const agregar =
            document.createElement(
                "button"
            );

        agregar.className =
            "operation";

        agregar.onclick =
            abrirAgregarPlanilla;

        agregar.innerHTML = `

            <div class="operation-icon">
                ➕
            </div>

            <strong>
                Agregar a planilla
            </strong>

            <small>
                Reincorporar un empleado activo excluido de esta planilla.
            </small>
        `;

        contenedor.prepend(
            agregar
        );
    }


    const tieneProcesar =
        Array.from(
            contenedor.querySelectorAll(
                "button"
            )
        ).some(
            boton =>
                boton.textContent
                    .toLowerCase()
                    .includes(
                        "procesar planilla"
                    )
        );


    if (!tieneProcesar) {

        const procesar =
            document.createElement(
                "button"
            );

        procesar.className =
            "operation";

        procesar.id =
            "btnProcesarPlanillaBD";

        procesar.onclick =
            procesarPlanillaBD;

        procesar.innerHTML = `

            <div class="operation-icon">
                ✅
            </div>

            <strong>
                Procesar planilla
            </strong>

            <small>
                Cerrar cálculos del período y conservar el historial.
            </small>
        `;

        contenedor.appendChild(
            procesar
        );
    }


    const tieneHistorial =
        Array.from(
            contenedor.querySelectorAll(
                "button"
            )
        ).some(
            boton =>
                boton.textContent
                    .toLowerCase()
                    .includes(
                        "historial de planillas"
                    )
        );


    if (!tieneHistorial) {

        const historial =
            document.createElement(
                "button"
            );

        historial.className =
            "operation";

        historial.onclick =
            abrirHistorialPlanillas;

        historial.innerHTML = `

            <div class="operation-icon">
                🗂️
            </div>

            <strong>
                Historial de planillas
            </strong>

            <small>
                Consultar períodos procesados y pagados en SQL Server.
            </small>
        `;

        contenedor.appendChild(
            historial
        );
    }
}


/* ============================================================
   CARGA DE PLANILLA
   ============================================================ */

async function cargarPlanillaBD() {

    try {

        let response =
            await fetch(
                `${API_PLANILLA}/actual`
            );


        let resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo consultar la planilla."
                )
            );
        }


        if (!resultado.existe) {

            response =
                await fetch(
                    `${API_PLANILLA}/sincronizar`,
                    {
                        method: "POST"
                    }
                );


            resultado =
                await planillaLeerRespuesta(
                    response
                );


            if (!response.ok) {

                throw new Error(
                    planillaMensajeError(
                        resultado,
                        "No se pudo sincronizar Personal."
                    )
                );
            }
        }


        planillaBD =
            resultado;


        renderizarPlanillaBD();

    } catch (error) {

        console.error(
            error
        );

        planillaBD =
            null;


        renderizarPlanillaBD();


        alert(
            "No se pudo cargar la planilla desde SQL Server: " +
            error.message
        );
    }
}


/* ============================================================
   TABLA PRINCIPAL
   ============================================================ */

function prepararEncabezadoPrincipal() {

    const tbody =
        document.getElementById(
            "tablaFinanzas"
        );

    if (!tbody) {
        return;
    }


    const tabla =
        tbody.closest(
            "table"
        );


    const fila =
        tabla?.querySelector(
            "thead tr"
        );


    if (!fila) {
        return;
    }


    const existeAcciones =
        Array.from(
            fila.querySelectorAll(
                "th"
            )
        ).some(
            th =>
                th.textContent
                    .trim()
                    .toUpperCase() ===
                "ACCIONES"
        );


    if (!existeAcciones) {

        const th =
            document.createElement(
                "th"
            );

        th.textContent =
            "ACCIONES";

        fila.appendChild(
            th
        );
    }
}


function obtenerDetallesFiltrados() {

    if (!planillaBD) {
        return [];
    }


    const busqueda =
        document.getElementById(
            "buscar"
        )?.value
        ?.trim()
        ?.toLowerCase() || "";


    const filtro =
        document.getElementById(
            "filtro"
        )?.value || "todos";


    return (
        planillaBD.detalles || []
    ).filter(
        detalle => {

            const nombre =
                String(
                    detalle.nombre_completo || ""
                ).toLowerCase();


            const cargo =
                String(
                    detalle.cargo || ""
                ).toLowerCase();


            const coincide =
                !busqueda ||
                nombre.includes(
                    busqueda
                ) ||
                cargo.includes(
                    busqueda
                );


            if (!coincide) {
                return false;
            }


            const salario =
                Number(
                    detalle.salario_base || 0
                );


            if (
                filtro === "alto" &&
                salario <= 20000
            ) {

                return false;
            }


            if (
                filtro === "bajo" &&
                salario >= 20000
            ) {

                return false;
            }


            return true;
        }
    );
}


function renderizarPlanillaBD() {

    prepararEncabezadoPrincipal();


    const tbody =
        document.getElementById(
            "tablaFinanzas"
        );


    if (!tbody) {
        return;
    }


    if (!planillaBD) {

        tbody.innerHTML = `

            <tr>

                <td
                    colspan="7"
                    style="
                        text-align:center;
                        padding:35px;
                        color:#8a9690;
                    "
                >
                    No se pudo cargar la planilla.
                </td>

            </tr>
        `;


        actualizarTotalesPlanillaBD();

        return;
    }


    const detalles =
        obtenerDetallesFiltrados();


    if (
        detalles.length === 0
    ) {

        tbody.innerHTML = `

            <tr>

                <td
                    colspan="7"
                    style="
                        text-align:center;
                        padding:35px;
                        color:#8a9690;
                    "
                >
                    No hay empleados en esta vista de la planilla.
                </td>

            </tr>
        `;


        actualizarTotalesPlanillaBD();

        return;
    }


    tbody.innerHTML =
        detalles
        .map(
            detalle => {

                const activo =
                    planillaDetalleActivo(
                        detalle
                    );


                return `

                    <tr>

                        <td>

                            <div class="employee">

                                <div class="avatar">
                                    ${
                                        planillaIniciales(
                                            detalle.nombre_completo
                                        )
                                    }
                                </div>


                                <div>

                                    <div class="employee-name">
                                        ${
                                            planillaTexto(
                                                detalle.nombre_completo
                                            )
                                        }
                                    </div>

                                    <div class="employee-role">
                                        ${
                                            planillaTexto(
                                                detalle.cargo
                                            )
                                        }
                                    </div>

                                </div>

                            </div>

                        </td>


                        <td class="money">

                            ${
                                planillaMoneda(
                                    detalle.salario_base
                                )
                            }

                        </td>


                        <td class="money negative">

                            ${
                                planillaMoneda(
                                    detalle.inss_laboral
                                )
                            }

                        </td>


                        <td class="money negative">

                            ${
                                planillaMoneda(
                                    detalle.ir_salario
                                )
                            }

                        </td>


                        <td class="money positive">

                            ${
                                planillaMoneda(
                                    detalle.salario_neto
                                )
                            }

                        </td>


                        <td>

                            <span
                                class="payroll-state ${
                                    activo
                                    ? "active"
                                    : "cancelled"
                                }"
                            >

                                ${
                                    activo
                                    ? "Activo"
                                    : "Excluido"
                                }

                            </span>

                        </td>


                        <td>

                            <div class="payroll-actions">

                                ${
                                    planillaEsPendiente() &&
                                    activo
                                    ? `

                                        <button
                                            class="payroll-action"
                                            onclick="modificarDetallePlanilla(${detalle.id_detalle})"
                                            title="Modificar"
                                        >
                                            ✏️
                                        </button>


                                        <button
                                            class="payroll-action danger"
                                            onclick="anularDetallePlanilla(${detalle.id_detalle})"
                                            title="Excluir de esta planilla"
                                        >
                                            🚫
                                        </button>

                                      `
                                    : ""
                                }


                                ${
                                    !activo &&
                                    planillaEsPendiente()
                                    ? `

                                        <button
                                            class="payroll-action success"
                                            onclick="agregarEmpleadoPlanilla(${detalle.id_empleado})"
                                            title="Reincorporar"
                                        >
                                            ♻️
                                        </button>

                                      `
                                    : ""
                                }

                            </div>

                        </td>

                    </tr>
                `;
            }
        )
        .join("");


    actualizarTotalesPlanillaBD();

    actualizarBotonProcesar();
}


/* ============================================================
   TOTALES
   ============================================================ */

function actualizarTotalesPlanillaBD() {

    const activos =
        (
            planillaBD?.detalles || []
        ).filter(
            planillaDetalleActivo
        );


    const totalEmpleados =
        activos.length;


    const bruto =
        activos.reduce(
            (
                total,
                item
            ) =>
                total +
                Number(
                    item.total_devengado || 0
                ),
            0
        );


    const inss =
        activos.reduce(
            (
                total,
                item
            ) =>
                total +
                Number(
                    item.inss_laboral || 0
                ),
            0
        );


    const neto =
        activos.reduce(
            (
                total,
                item
            ) =>
                total +
                Number(
                    item.salario_neto || 0
                ),
            0
        );


    if (
        document.getElementById(
            "totalEmpleados"
        )
    ) {

        document.getElementById(
            "totalEmpleados"
        ).textContent =
            totalEmpleados;
    }


    if (
        document.getElementById(
            "totalBruto"
        )
    ) {

        document.getElementById(
            "totalBruto"
        ).textContent =
            planillaMoneda(
                bruto
            );
    }


    if (
        document.getElementById(
            "totalInss"
        )
    ) {

        document.getElementById(
            "totalInss"
        ).textContent =
            planillaMoneda(
                inss
            );
    }


    if (
        document.getElementById(
            "totalNeto"
        )
    ) {

        document.getElementById(
            "totalNeto"
        ).textContent =
            planillaMoneda(
                neto
            );
    }
}


/* ============================================================
   BOTÓN PROCESAR
   ============================================================ */

function actualizarBotonProcesar() {

    const boton =
        document.getElementById(
            "btnProcesarPlanillaBD"
        );


    if (
        !boton ||
        !planillaBD
    ) {

        return;
    }


    const estado =
        String(
            planillaBD.estado || ""
        );


    boton.disabled =
        estado !== "Pendiente";


    boton.style.opacity =
        estado === "Pendiente"
        ? "1"
        : ".55";


    const strong =
        boton.querySelector(
            "strong"
        );


    const small =
        boton.querySelector(
            "small"
        );


    if (
        estado === "Procesada"
    ) {

        strong.textContent =
            "Planilla procesada";


        small.textContent =
            "La planilla ya está cerrada para modificaciones.";
    }


    if (
        estado === "Pagada"
    ) {

        strong.textContent =
            "Planilla pagada";


        small.textContent =
            "El pago ya fue registrado como egreso financiero.";
    }
}


/* ============================================================
   OBSERVAR PLANILLA
   ============================================================ */

function observarPlanillaBD() {

    if (!planillaBD) {

        alert(
            "La planilla todavía no está disponible."
        );

        return;
    }


    const activos =
        (
            planillaBD.detalles || []
        ).filter(
            planillaDetalleActivo
        );


    const bruto =
        activos.reduce(
            (
                total,
                item
            ) =>
                total +
                Number(
                    item.total_devengado || 0
                ),
            0
        );


    const inss =
        activos.reduce(
            (
                total,
                item
            ) =>
                total +
                Number(
                    item.inss_laboral || 0
                ),
            0
        );


    const neto =
        activos.reduce(
            (
                total,
                item
            ) =>
                total +
                Number(
                    item.salario_neto || 0
                ),
            0
        );


    document.getElementById(
        "modalEmpleados"
    ).textContent =
        activos.length;


    document.getElementById(
        "modalBruto"
    ).textContent =
        planillaMoneda(
            bruto
        );


    document.getElementById(
        "modalInss"
    ).textContent =
        planillaMoneda(
            inss
        );


    document.getElementById(
        "modalNeto"
    ).textContent =
        planillaMoneda(
            neto
        );


    const tbody =
        document.getElementById(
            "tablaPlanilla"
        );


    tbody.innerHTML =
        activos.length === 0
        ? `

            <tr>

                <td
                    colspan="7"
                    style="
                        text-align:center;
                        padding:35px;
                        color:#8a9690;
                    "
                >
                    No hay empleados activos en esta planilla.
                </td>

            </tr>
          `
        : activos
            .map(
                detalle => `

                    <tr>

                        <td>
                            ${
                                planillaTexto(
                                    detalle.nombre_completo
                                )
                            }
                        </td>

                        <td class="money">
                            ${
                                planillaMoneda(
                                    detalle.total_devengado
                                )
                            }
                        </td>

                        <td class="money negative">
                            ${
                                planillaMoneda(
                                    detalle.inss_laboral
                                )
                            }
                        </td>

                        <td class="money">
                            ${
                                planillaMoneda(
                                    detalle.salario_gravable_anual
                                )
                            }
                        </td>

                        <td class="money negative">
                            ${
                                planillaMoneda(
                                    detalle.ir_anual
                                )
                            }
                        </td>

                        <td class="money negative">
                            ${
                                planillaMoneda(
                                    detalle.ir_salario
                                )
                            }
                        </td>

                        <td class="money positive">
                            ${
                                planillaMoneda(
                                    detalle.salario_neto
                                )
                            }
                        </td>

                    </tr>
                `
            )
            .join("");


    document.getElementById(
        "modalPlanilla"
    ).classList.add(
        "show"
    );


    instalarAccionesModalPrincipal();
}


/* ============================================================
   BOTONES DENTRO DEL MODAL PRINCIPAL
   ============================================================ */

function instalarAccionesModalPrincipal() {

    const modal =
        document.getElementById(
            "modalPlanilla"
        );


    if (
        !modal ||
        !planillaBD
    ) {

        return;
    }


    const contenedor =
        modal.querySelector(
            ".modal-content > div[style*='text-align:right']"
        );


    if (!contenedor) {
        return;
    }


    Array.from(
        contenedor.querySelectorAll(
            "[data-planilla-db]"
        )
    ).forEach(
        elemento =>
            elemento.remove()
    );


    if (
        planillaBD.estado ===
        "Pendiente"
    ) {

        const boton =
            document.createElement(
                "button"
            );


        boton.className =
            "btn btn-primary";


        boton.dataset.planillaDb =
            "procesar";


        boton.style.marginRight =
            "8px";


        boton.textContent =
            "✅ Procesar planilla";


        boton.onclick =
            procesarPlanillaBD;


        contenedor.prepend(
            boton
        );
    }


    if (
        planillaBD.estado ===
        "Procesada"
    ) {

        const boton =
            document.createElement(
                "button"
            );


        boton.className =
            "btn btn-primary";


        boton.dataset.planillaDb =
            "pagar";


        boton.style.marginRight =
            "8px";


        boton.textContent =
            "💳 Marcar como pagada";


        boton.onclick =
            abrirPagoPlanilla;


        contenedor.prepend(
            boton
        );
    }
}


/* ============================================================
   SINCRONIZAR PERSONAL
   ============================================================ */

async function sincronizarPlanillaBD() {

    try {

        const response =
            await fetch(
                `${API_PLANILLA}/sincronizar`,
                {
                    method: "POST"
                }
            );


        const resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo sincronizar Personal."
                )
            );
        }


        planillaBD =
            resultado;


        renderizarPlanillaBD();


        alert(
            resultado.mensaje ||
            "Personal sincronizado correctamente."
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


/* ============================================================
   AGREGAR / REINCORPORAR
   ============================================================ */

async function abrirAgregarPlanilla() {

    if (
        !planillaEsPendiente()
    ) {

        alert(
            "Solo puedes agregar empleados mientras la planilla esté pendiente."
        );

        return;
    }


    try {

        const response =
            await fetch(
                `${API_PLANILLA}/disponibles`
            );


        const disponibles =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    disponibles,
                    "No se pudieron consultar los empleados disponibles."
                )
            );
        }


        if (
            !Array.isArray(
                disponibles
            ) ||
            disponibles.length === 0
        ) {

            alert(
                "No hay empleados activos pendientes de agregar a la planilla."
            );

            return;
        }


        abrirModalPlanillaBD(

            "Agregar a planilla",

            "Solo se muestran empleados activos del módulo Personal.",

            `

                <div class="payroll-db-grid">

                    <div class="payroll-db-field full">

                        <label>
                            Empleado *
                        </label>


                        <select id="planillaAgregarEmpleado">

                            ${
                                disponibles
                                .map(
                                    item => `

                                        <option
                                            value="${item.id_empleado}"
                                        >
                                            ${
                                                planillaTexto(
                                                    item.nombre_completo
                                                )
                                            }

                                            —

                                            ${
                                                planillaTexto(
                                                    item.cargo
                                                )
                                            }

                                        </option>
                                    `
                                )
                                .join("")
                            }

                        </select>

                    </div>

                </div>


                <div class="payroll-db-footer">

                    <button
                        class="payroll-btn-secondary"
                        onclick="cerrarModalPlanillaBD()"
                    >
                        Cancelar
                    </button>


                    <button
                        class="payroll-btn-primary"
                        onclick="confirmarAgregarPlanilla()"
                    >
                        ➕ Agregar
                    </button>

                </div>
            `
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


async function confirmarAgregarPlanilla() {

    const idEmpleado =
        Number(
            document.getElementById(
                "planillaAgregarEmpleado"
            ).value
        );


    await agregarEmpleadoPlanilla(
        idEmpleado
    );


    cerrarModalPlanillaBD();
}


async function agregarEmpleadoPlanilla(
    idEmpleado
) {

    try {

        const response =
            await fetch(
                `${API_PLANILLA}/agregar/${idEmpleado}`,
                {
                    method: "POST"
                }
            );


        const resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo agregar el empleado."
                )
            );
        }


        planillaBD =
            resultado;


        renderizarPlanillaBD();


        alert(
            resultado.mensaje ||
            "Empleado agregado correctamente."
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


/* ============================================================
   MODIFICAR DETALLE
   ============================================================ */

function modificarDetallePlanilla(
    idDetalle
) {

    if (
        !planillaEsPendiente()
    ) {

        return;
    }


    const detalle =
        (
            planillaBD.detalles || []
        ).find(
            item =>
                Number(
                    item.id_detalle
                ) ===
                Number(
                    idDetalle
                )
        );


    if (!detalle) {
        return;
    }


    abrirModalPlanillaBD(

        `Modificar: ${detalle.nombre_completo}`,

        "Los cambios solo afectan esta planilla; no modifican el salario registrado en Personal.",

        `

            <form
                id="formModificarPlanilla"
                onsubmit="guardarDetallePlanilla(event,${detalle.id_detalle})"
            >

                <div class="payroll-db-grid">


                    <div class="payroll-db-field">

                        <label>
                            Salario base
                        </label>

                        <input
                            value="${
                                Number(
                                    detalle.salario_base || 0
                                ).toFixed(2)
                            }"
                            readonly
                        >

                    </div>


                    <div class="payroll-db-field">

                        <label>
                            Días trabajados *
                        </label>

                        <input
                            id="planillaDias"
                            type="number"
                            min="0"
                            max="30"
                            step="0.01"
                            value="${
                                Number(
                                    detalle.dias_trabajados || 0
                                )
                            }"
                            required
                        >

                    </div>


                    <div class="payroll-db-field">

                        <label>
                            Horas extra
                        </label>

                        <input
                            id="planillaHorasExtra"
                            type="number"
                            min="0"
                            step="0.01"
                            value="${
                                Number(
                                    detalle.horas_extra || 0
                                )
                            }"
                        >

                    </div>


                    <div class="payroll-db-field">

                        <label>
                            Monto por horas extra
                        </label>

                        <input
                            id="planillaMontoExtra"
                            type="number"
                            min="0"
                            step="0.01"
                            value="${
                                Number(
                                    detalle.monto_horas_extra || 0
                                )
                            }"
                        >

                    </div>


                    <div class="payroll-db-field">

                        <label>
                            Bonificaciones
                        </label>

                        <input
                            id="planillaBonificaciones"
                            type="number"
                            min="0"
                            step="0.01"
                            value="${
                                Number(
                                    detalle.bonificaciones || 0
                                )
                            }"
                        >

                    </div>


                    <div class="payroll-db-field">

                        <label>
                            Otras deducciones
                        </label>

                        <input
                            id="planillaOtrasDeducciones"
                            type="number"
                            min="0"
                            step="0.01"
                            value="${
                                Number(
                                    detalle.otras_deducciones || 0
                                )
                            }"
                        >

                    </div>


                    <div class="payroll-db-field full">

                        <label>
                            Observaciones
                        </label>

                        <textarea
                            id="planillaObservaciones"
                            maxlength="200"
                        >${
                            detalle.observaciones
                            ? planillaTexto(
                                detalle.observaciones
                              )
                            : ""
                        }</textarea>

                    </div>

                </div>


                <div class="payroll-db-footer">

                    <button
                        type="button"
                        class="payroll-btn-secondary"
                        onclick="cerrarModalPlanillaBD()"
                    >
                        Cancelar
                    </button>


                    <button
                        type="submit"
                        class="payroll-btn-primary"
                    >
                        💾 Guardar cambios
                    </button>

                </div>

            </form>
        `
    );
}


async function guardarDetallePlanilla(
    event,
    idDetalle
) {

    event.preventDefault();


    const datos = {

        diasTrabajados:
            Number(
                document.getElementById(
                    "planillaDias"
                ).value || 0
            ),


        horasExtra:
            Number(
                document.getElementById(
                    "planillaHorasExtra"
                ).value || 0
            ),


        montoHorasExtra:
            Number(
                document.getElementById(
                    "planillaMontoExtra"
                ).value || 0
            ),


        bonificaciones:
            Number(
                document.getElementById(
                    "planillaBonificaciones"
                ).value || 0
            ),


        otrasDeducciones:
            Number(
                document.getElementById(
                    "planillaOtrasDeducciones"
                ).value || 0
            ),


        observaciones:
            document.getElementById(
                "planillaObservaciones"
            ).value.trim()
    };


    const boton =
        event.submitter;


    if (boton) {

        boton.disabled =
            true;

        boton.textContent =
            "Guardando...";
    }


    try {

        const response =
            await fetch(
                `${API_PLANILLA}/detalles/${idDetalle}`,
                {
                    method: "PUT",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body:
                        JSON.stringify(
                            datos
                        )
                }
            );


        const resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo modificar la planilla."
                )
            );
        }


        planillaBD =
            resultado;


        cerrarModalPlanillaBD();


        renderizarPlanillaBD();


        alert(
            resultado.mensaje ||
            "Detalle modificado correctamente."
        );

    } catch (error) {

        alert(
            error.message
        );

    } finally {

        if (boton) {

            boton.disabled =
                false;

            boton.textContent =
                "💾 Guardar cambios";
        }
    }
}


/* ============================================================
   EXCLUIR EMPLEADO DE ESTA PLANILLA
   ============================================================ */

function anularDetallePlanilla(
    idDetalle
) {

    const detalle =
        (
            planillaBD?.detalles || []
        ).find(
            item =>
                Number(
                    item.id_detalle
                ) ===
                Number(
                    idDetalle
                )
        );


    if (!detalle) {
        return;
    }


    abrirModalPlanillaBD(

        "Excluir de esta planilla",

        "El empleado seguirá existiendo y activo en el módulo Personal.",

        `

            <div class="payroll-db-field">

                <label>
                    Motivo de la exclusión *
                </label>


                <textarea
                    id="planillaMotivoAnulacion"
                    maxlength="200"
                    placeholder="Ej. No corresponde pago en este período..."
                ></textarea>

            </div>


            <div class="payroll-db-footer">

                <button
                    class="payroll-btn-secondary"
                    onclick="cerrarModalPlanillaBD()"
                >
                    Cancelar
                </button>


                <button
                    class="payroll-btn-danger"
                    onclick="confirmarAnularDetallePlanilla(${idDetalle})"
                >
                    🚫 Excluir
                </button>

            </div>
        `
    );
}


async function confirmarAnularDetallePlanilla(
    idDetalle
) {

    const motivo =
        document.getElementById(
            "planillaMotivoAnulacion"
        ).value.trim();


    if (!motivo) {

        alert(
            "Indica el motivo de la exclusión."
        );

        return;
    }


    try {

        const response =
            await fetch(
                `${API_PLANILLA}/detalles/${idDetalle}/anular`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body:
                        JSON.stringify({
                            motivo
                        })
                }
            );


        const resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo excluir al empleado."
                )
            );
        }


        planillaBD =
            resultado;


        cerrarModalPlanillaBD();


        renderizarPlanillaBD();


        alert(
            resultado.mensaje
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


/* ============================================================
   PROCESAR PLANILLA
   ============================================================ */

async function procesarPlanillaBD() {

    if (
        !planillaEsPendiente()
    ) {

        alert(
            "La planilla ya no está pendiente."
        );

        return;
    }


    if (
        !confirm(
            "¿Deseas procesar esta planilla? Después ya no podrás modificar sus empleados ni cálculos."
        )
    ) {

        return;
    }


    try {

        const response =
            await fetch(
                `${API_PLANILLA}/procesar`,
                {
                    method: "POST"
                }
            );


        const resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo procesar la planilla."
                )
            );
        }


        planillaBD =
            resultado;


        renderizarPlanillaBD();


        observarPlanillaBD();


        alert(
            resultado.mensaje ||
            "Planilla procesada correctamente."
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


/* ============================================================
   PAGO DE PLANILLA
   ============================================================ */

function abrirPagoPlanilla() {

    if (
        !planillaBD ||
        planillaBD.estado !==
        "Procesada"
    ) {

        return;
    }


    abrirModalPlanillaBD(

        "Registrar pago de planilla",

        `Total a pagar: ${
            planillaMoneda(
                planillaBD.total_pagar
            )
        }`,

        `

            <div class="payroll-db-field">

                <label>
                    Método de pago *
                </label>


                <select id="planillaMetodoPago">

                    <option value="Transferencia">
                        Transferencia
                    </option>

                    <option value="Efectivo">
                        Efectivo
                    </option>

                    <option value="Cheque">
                        Cheque
                    </option>

                </select>

            </div>


            <div class="payroll-db-footer">

                <button
                    class="payroll-btn-secondary"
                    onclick="cerrarModalPlanillaBD()"
                >
                    Cancelar
                </button>


                <button
                    class="payroll-btn-primary"
                    onclick="confirmarPagoPlanilla()"
                >
                    💳 Registrar pago
                </button>

            </div>
        `
    );
}


async function confirmarPagoPlanilla() {

    const metodoPago =
        document.getElementById(
            "planillaMetodoPago"
        ).value;


    try {

        const response =
            await fetch(
                `${API_PLANILLA}/${planillaBD.id_nomina}/pagar`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type":
                            "application/json"
                    },

                    body:
                        JSON.stringify({
                            metodoPago
                        })
                }
            );


        const resultado =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    resultado,
                    "No se pudo registrar el pago."
                )
            );
        }


        planillaBD =
            resultado;


        cerrarModalPlanillaBD();


        renderizarPlanillaBD();


        alert(
            resultado.mensaje ||
            "Pago registrado correctamente."
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


/* ============================================================
   HISTORIAL DE PLANILLAS
   ============================================================ */

async function abrirHistorialPlanillas() {

    try {

        const response =
            await fetch(
                `${API_PLANILLA}/historial`
            );


        const historial =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    historial,
                    "No se pudo cargar el historial."
                )
            );
        }


        const filas =
            Array.isArray(
                historial
            ) &&
            historial.length > 0

            ? historial
                .map(
                    item => `

                        <tr>

                            <td>
                                #${item.id_nomina}
                            </td>


                            <td>
                                ${
                                    planillaTexto(
                                        item.fecha_inicio
                                    )
                                }

                                al

                                ${
                                    planillaTexto(
                                        item.fecha_fin
                                    )
                                }
                            </td>


                            <td>
                                ${
                                    planillaTexto(
                                        item.estado
                                    )
                                }
                            </td>


                            <td>
                                ${
                                    planillaMoneda(
                                        item.total_devengado
                                    )
                                }
                            </td>


                            <td>
                                ${
                                    planillaMoneda(
                                        item.total_deducciones
                                    )
                                }
                            </td>


                            <td>
                                ${
                                    planillaMoneda(
                                        item.total_pagar
                                    )
                                }
                            </td>

                        </tr>
                    `
                )
                .join("")

            : `

                <tr>

                    <td
                        colspan="6"
                        style="
                            text-align:center;
                            padding:25px;
                            color:#7c8982;
                        "
                    >
                        No hay planillas registradas todavía.
                    </td>

                </tr>
              `;


        abrirModalPlanillaBD(

            "Historial de planillas",

            "Períodos guardados en SQL Server.",

            `

                <div style="overflow:auto">

                    <table
                        style="
                            width:100%;
                            border-collapse:collapse;
                        "
                    >

                        <thead>

                            <tr>

                                <th style="padding:9px;text-align:left">
                                    ID
                                </th>

                                <th style="padding:9px;text-align:left">
                                    PERÍODO
                                </th>

                                <th style="padding:9px;text-align:left">
                                    ESTADO
                                </th>

                                <th style="padding:9px;text-align:left">
                                    DEVENGADO
                                </th>

                                <th style="padding:9px;text-align:left">
                                    DEDUCCIONES
                                </th>

                                <th style="padding:9px;text-align:left">
                                    NETO
                                </th>

                            </tr>

                        </thead>


                        <tbody>

                            ${filas}

                        </tbody>

                    </table>

                </div>


                <div class="payroll-db-footer">

                    <button
                        class="payroll-btn-secondary"
                        onclick="cerrarModalPlanillaBD()"
                    >
                        Cerrar
                    </button>

                </div>
            `
        );

    } catch (error) {

        alert(
            error.message
        );
    }
}


/* ============================================================
   IMPRIMIR PLANILLA
   ============================================================ */

async function imprimirPlanillaBD() {

    try {

        const response = await fetch(
            `${API_PLANILLA}/historial`
        );

        const historial =
            await planillaLeerRespuesta(response);


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    historial,
                    "No se pudieron consultar las planillas."
                )
            );
        }


        if (
            !Array.isArray(historial) ||
            historial.length === 0
        ) {

            alert(
                "No existen planillas registradas para imprimir."
            );

            return;
        }


        const opciones =
            historial
            .map(planilla => {

                const fechaInicio =
                    planilla.fecha_inicio || "";

                const fechaFin =
                    planilla.fecha_fin || "";

                const estado =
                    planilla.estado || "Sin estado";


                return `

                    <option value="${planilla.id_nomina}">

                        Planilla #${planilla.id_nomina}
                        —
                        ${fechaInicio}
                        al
                        ${fechaFin}
                        —
                        ${estado}

                    </option>
                `;
            })
            .join("");


        abrirModalPlanillaBD(

            "Imprimir planilla",

            "Selecciona la planilla que deseas imprimir.",

            `

                <div class="payroll-db-grid">

                    <div class="payroll-db-field full">

                        <label>
                            Planilla *
                        </label>

                        <select id="planillaSeleccionImprimir">

                            ${opciones}

                        </select>

                    </div>

                </div>


                <div class="payroll-db-footer">

                    <button
                        class="payroll-btn-secondary"
                        onclick="cerrarModalPlanillaBD()"
                    >
                        Cancelar
                    </button>


                    <button
                        class="payroll-btn-primary"
                        onclick="confirmarImpresionPlanilla()"
                    >
                        🖨️ Imprimir seleccionada
                    </button>

                </div>
            `
        );


    } catch (error) {

        console.error(error);

        alert(
            error.message
        );
    }
}


/* ============================================================
   CARGAR PLANILLA SELECCIONADA
   ============================================================ */

async function confirmarImpresionPlanilla() {

    const select =
        document.getElementById(
            "planillaSeleccionImprimir"
        );


    if (!select) {
        return;
    }


    const idNomina =
        Number(select.value);


    if (!idNomina) {

        alert(
            "Selecciona una planilla."
        );

        return;
    }


    try {

        const response =
            await fetch(
                `${API_PLANILLA}/${idNomina}`
            );


        const planilla =
            await planillaLeerRespuesta(
                response
            );


        if (!response.ok) {

            throw new Error(
                planillaMensajeError(
                    planilla,
                    "No se pudo cargar la planilla seleccionada."
                )
            );
        }


        cerrarModalPlanillaBD();


        imprimirPlanillaSeleccionada(
            planilla
        );


    } catch (error) {

        console.error(error);

        alert(
            error.message
        );
    }
}


/* ============================================================
   GENERAR DOCUMENTO DE IMPRESIÓN
   ============================================================ */

function imprimirPlanillaSeleccionada(
    planilla
) {

    if (!planilla) {

        alert(
            "No se pudo obtener la planilla."
        );

        return;
    }


    const activos =
        (
            planilla.detalles || []
        ).filter(
            planillaDetalleActivo
        );


    const filas =
        activos
        .map(
            detalle => `

                <tr>

                    <td>
                        ${
                            planillaTexto(
                                detalle.codigo_empleado
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaTexto(
                                detalle.nombre_completo
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaTexto(
                                detalle.cargo
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaMoneda(
                                detalle.salario_base
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaMoneda(
                                detalle.total_devengado
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaMoneda(
                                detalle.inss_laboral
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaMoneda(
                                detalle.ir_salario
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaMoneda(
                                detalle.otras_deducciones
                            )
                        }
                    </td>


                    <td>
                        ${
                            planillaMoneda(
                                detalle.salario_neto
                            )
                        }
                    </td>

                </tr>
            `
        )
        .join("");


    const ventana =
        window.open(
            "",
            "_blank"
        );


    if (!ventana) {

        alert(
            "El navegador bloqueó la ventana de impresión."
        );

        return;
    }


    ventana.document.write(`

        <!DOCTYPE html>

        <html lang="es">

        <head>

            <meta charset="UTF-8">

            <title>
                Planilla #${planilla.id_nomina}
            </title>


            <style>

                * {
                    box-sizing: border-box;
                }


                body {
                    font-family: Arial, sans-serif;
                    padding: 28px;
                    color: #1d2d25;
                }


                .header {
                    border-bottom: 2px solid #174b36;
                    padding-bottom: 15px;
                    margin-bottom: 20px;
                }


                h1 {
                    margin: 0 0 7px;
                    color: #174b36;
                }


                .subtitle {
                    color: #65736c;
                    margin: 4px 0;
                }


                .info {
                    display: grid;
                    grid-template-columns: repeat(3, 1fr);
                    gap: 10px;
                    margin: 20px 0;
                }


                .info-box {
                    border: 1px solid #dde5e1;
                    border-radius: 8px;
                    padding: 10px;
                }


                .info-box small {
                    display: block;
                    color: #78867f;
                    margin-bottom: 4px;
                }


                .info-box strong {
                    font-size: 14px;
                }


                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 20px;
                }


                th,
                td {
                    border: 1px solid #d9e0dc;
                    padding: 8px;
                    font-size: 11px;
                    text-align: left;
                }


                th {
                    background: #f2f5f3;
                    color: #33463d;
                }


                .money {
                    text-align: right;
                }


                .totales {
                    margin-top: 25px;
                    margin-left: auto;
                    width: 330px;
                }


                .total-row {
                    display: flex;
                    justify-content: space-between;
                    padding: 7px 0;
                    border-bottom: 1px solid #e4e9e6;
                }


                .total-final {
                    font-size: 16px;
                    font-weight: bold;
                    color: #174b36;
                    border-top: 2px solid #174b36;
                    margin-top: 5px;
                    padding-top: 10px;
                }


                .footer {
                    margin-top: 45px;
                    color: #7b8781;
                    font-size: 10px;
                    text-align: center;
                }


                @media print {

                    body {
                        padding: 10px;
                    }

                }

            </style>

        </head>


        <body>


            <div class="header">

                <h1>
                    Gestión Bovina
                </h1>


                <div class="subtitle">
                    Planilla salarial
                    #${planilla.id_nomina}
                </div>


                <div class="subtitle">

                    Período:

                    ${
                        planillaTexto(
                            planilla.fecha_inicio
                        )
                    }

                    al

                    ${
                        planillaTexto(
                            planilla.fecha_fin
                        )
                    }

                </div>

            </div>


            <div class="info">


                <div class="info-box">

                    <small>
                        Estado
                    </small>

                    <strong>
                        ${
                            planillaTexto(
                                planilla.estado
                            )
                        }
                    </strong>

                </div>


                <div class="info-box">

                    <small>
                        Empleados
                    </small>

                    <strong>
                        ${activos.length}
                    </strong>

                </div>


                <div class="info-box">

                    <small>
                        Fecha de pago
                    </small>

                    <strong>
                        ${
                            planilla.fecha_pago
                            ? planillaTexto(
                                planilla.fecha_pago
                              )
                            : "Pendiente"
                        }
                    </strong>

                </div>


            </div>


            <table>


                <thead>

                    <tr>

                        <th>
                            CÓDIGO
                        </th>

                        <th>
                            EMPLEADO
                        </th>

                        <th>
                            CARGO
                        </th>

                        <th>
                            SALARIO BASE
                        </th>

                        <th>
                            DEVENGADO
                        </th>

                        <th>
                            INSS
                        </th>

                        <th>
                            IR
                        </th>

                        <th>
                            OTRAS DED.
                        </th>

                        <th>
                            NETO
                        </th>

                    </tr>

                </thead>


                <tbody>

                    ${
                        filas ||
                        `

                        <tr>

                            <td
                                colspan="9"
                                style="
                                    text-align:center;
                                    padding:30px;
                                "
                            >
                                No hay empleados en esta planilla.
                            </td>

                        </tr>
                        `
                    }

                </tbody>


            </table>


            <div class="totales">


                <div class="total-row">

                    <span>
                        Total devengado
                    </span>

                    <strong>
                        ${
                            planillaMoneda(
                                planilla.total_devengado
                            )
                        }
                    </strong>

                </div>


                <div class="total-row">

                    <span>
                        Total deducciones
                    </span>

                    <strong>
                        ${
                            planillaMoneda(
                                planilla.total_deducciones
                            )
                        }
                    </strong>

                </div>


                <div class="total-row">

                    <span>
                        INSS patronal
                    </span>

                    <strong>
                        ${
                            planillaMoneda(
                                planilla.total_inss_patronal
                            )
                        }
                    </strong>

                </div>


                <div class="total-row">

                    <span>
                        INATEC
                    </span>

                    <strong>
                        ${
                            planillaMoneda(
                                planilla.total_inatec
                            )
                        }
                    </strong>

                </div>


                <div class="total-row total-final">

                    <span>
                        NETO A PAGAR
                    </span>

                    <strong>
                        ${
                            planillaMoneda(
                                planilla.total_pagar
                            )
                        }
                    </strong>

                </div>


            </div>


            <div class="footer">

                Gestión Bovina —
                Sistema de administración ganadera

            </div>


        </body>

        </html>
    `);


    ventana.document.close();


    ventana.focus();


    setTimeout(
        () => {

            ventana.print();

        },
        300
    );
}
/* ============================================================
   REEMPLAZAR FUNCIONES ANTIGUAS DE Finanzas.html
   ============================================================ */

window.renderizar =
    renderizarPlanillaBD;


window.observarPlanilla =
    observarPlanillaBD;


window.sincronizar =
    sincronizarPlanillaBD;


window.imprimirPlanilla =
    imprimirPlanillaBD;


window.agregarPlanilla =
    abrirAgregarPlanilla;


window.agregarAPlanilla =
    abrirAgregarPlanilla;


/* ============================================================
   INICIO
   ============================================================ */

instalarInterfazPlanilla();

cargarPlanillaBD();


/* Funcion de planilla de aguinaldo para los botones */
async function abrirAguinaldo() {
    try {
        const respuesta = await fetch('/api/aguinaldo/calcular');
        if (!respuesta.ok) throw new Error('Error al calcular aguinaldo');
        
        const datos = await respuesta.json();
        renderizarTablaAguinaldo(datos.detalles);
    } catch (error) {
        console.error(error);
        alert('No se pudo calcular el aguinaldo: ' + error.message);
    }
}

function renderizarTablaAguinaldo(detalles) {
    const tbody = document.querySelector('#tablaAguinaldo tbody');
    tbody.innerHTML = '';

    detalles.forEach(d => {
        const fila = `
            <tr>
                <td>${d.id_empleado}</td>
                <td>${d.nombre_completo}</td>
                <td>C$ ${Number(d.salario_mensual).toFixed(2)}</td>
                <td>C$ ${Number(d.total_anual).toFixed(2)}</td>
                <td>C$ ${Number(d.aguinaldo).toFixed(2)}</td>
            </tr>
        `;
        tbody.innerHTML += fila;
    });
}

async function guardarAguinaldo() {
    if (!confirm('¿Guardar el aguinaldo de este año?')) return;

    try {
        const respuesta = await fetch('/api/aguinaldo/guardar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
        });

        const datos = await respuesta.json();
        alert(datos.mensaje || 'Aguinaldo guardado');
    } catch (error) {
        alert('Error al guardar: ' + error.message);
    }
}