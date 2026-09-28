/* =========================================================
   GESTIÓN BOVINA
   SISTEMA DE PERMISOS Y NAVEGACIÓN
   ========================================================= */


/* =========================================================
   PERMISOS POR ROL
   ========================================================= */

const PERMISOS = {

    ADMIN: [
        "MAIN",
        "GANADERIA",
        "SUBASTAS",
        "VETERINARIA",
        "INVENTARIO",
        "FINANZAS",
        "PERSONAL"
    ],

    GANADERIA: [
        "GANADERIA",
        "SUBASTAS"
    ],

    VETERINARIA: [
        "VETERINARIA"
    ],

    INVENTARIO: [
        "INVENTARIO"
    ],

    FINANZAS: [
        "FINANZAS"
    ],

    PERSONAL: [
        "PERSONAL"
    ]

};


/* =========================================================
   SABER EN QUÉ CARPETA ESTAMOS
   ========================================================= */

function estaEnModulos() {

    return window.location.pathname
        .replace(/\\/g, "/")
        .includes("/modulos/");

}


function estaEnProyecto() {

    return window.location.pathname
        .replace(/\\/g, "/")
        .includes("/proyecto/");

}


/* =========================================================
   RUTAS DE LOS MÓDULOS
   ========================================================= */

function obtenerRutaModulo(modulo) {

    const desdeModulos =
        estaEnModulos();


    const rutas = {

        MAIN:
            desdeModulos
                ? "../proyecto/Main.html"
                : "Main.html",

        GANADERIA:
            desdeModulos
                ? "GestionGanado.html"
                : "../modulos/GestionGanado.html",

        SUBASTAS:
            desdeModulos
                ? "Subastas.html"
                : "../modulos/Subastas.html",

        VETERINARIA:
            desdeModulos
                ? "Veterinaria.html"
                : "../modulos/Veterinaria.html",

        INVENTARIO:
            desdeModulos
                ? "Inventario.html"
                : "../modulos/Inventario.html",

        FINANZAS:
            desdeModulos
                ? "Finanzas.html"
                : "../modulos/Finanzas.html",

        PERSONAL:
            desdeModulos
                ? "Personal.html"
                : "../modulos/Personal.html"

    };


    return rutas[modulo] || null;

}


/* =========================================================
   RUTA DEL LOGIN
   ========================================================= */

function obtenerRutaLogin() {

    if (estaEnModulos()) {

        return "../proyecto/Login.html";

    }

    return "Login.html";

}


/* =========================================================
   OBTENER ROL ACTUAL
   ========================================================= */

function obtenerRolActual() {

    const sesion =
        obtenerSesion();


    if (!sesion) {

        return null;

    }


    return String(
        sesion.rol || ""
    ).toUpperCase();

}


/* =========================================================
   COMPROBAR PERMISO
   ========================================================= */

function tienePermiso(modulo) {

    const sesion =
        obtenerSesion();


    if (!sesion) {

        return false;

    }


    const rol =
        String(
            sesion.rol || ""
        ).toUpperCase();


    const permisos =
        PERMISOS[rol];


    if (!permisos) {

        return false;

    }


    return permisos.includes(
        String(modulo || "")
            .toUpperCase()
    );

}


/* =========================================================
   NAVEGAR A UN MÓDULO
   ========================================================= */

function irAModulo(modulo) {

    modulo =
        String(
            modulo || ""
        ).toUpperCase();


    if (!tienePermiso(modulo)) {

        alert(
            "Acceso denegado.\n\n" +
            "No tienes permisos para acceder a este módulo."
        );

        return;

    }


    const ruta =
        obtenerRutaModulo(
            modulo
        );


    if (!ruta) {

        console.error(
            "No existe una ruta para el módulo:",
            modulo
        );

        return;

    }


    window.location.href =
        ruta;

}


/* =========================================================
   PROTEGER MÓDULO
   ========================================================= */

function protegerModulo(modulo) {

    const sesion =
        obtenerSesion();


    if (!sesion) {

        window.location.href =
            obtenerRutaLogin();

        return false;

    }


    if (!tienePermiso(modulo)) {

        alert(
            "Acceso denegado.\n\n" +
            "No tienes permisos para acceder a este módulo."
        );


        const paginaPrincipal =
            obtenerPaginaPrincipal(
                sesion.rol
            );


        window.location.href =
            paginaPrincipal;


        return false;

    }


    return true;

}


/* =========================================================
   PÁGINA PRINCIPAL SEGÚN ROL
   ========================================================= */

function obtenerPaginaPrincipal(rol) {

    rol =
        String(
            rol || ""
        ).toUpperCase();


    switch (rol) {

        case "ADMIN":

            return obtenerRutaModulo(
                "MAIN"
            );


        case "GANADERIA":

            return obtenerRutaModulo(
                "GANADERIA"
            );


        case "VETERINARIA":

            return obtenerRutaModulo(
                "VETERINARIA"
            );


        case "INVENTARIO":

            return obtenerRutaModulo(
                "INVENTARIO"
            );


        case "FINANZAS":

            return obtenerRutaModulo(
                "FINANZAS"
            );


        case "PERSONAL":

            return obtenerRutaModulo(
                "PERSONAL"
            );


        default:

            return obtenerRutaLogin();

    }

}


/* =========================================================
   ICONO PROPIO PARA SUBASTAS
   ========================================================= */

function obtenerIconoSubastas() {

    return `

        <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
            style="
                width:20px;
                height:20px;
                display:block;
                fill:none;
                stroke:currentColor;
                stroke-width:1.8;
                stroke-linecap:round;
                stroke-linejoin:round;
            "
        >

            <path d="M14 4 20 10"></path>

            <path d="m12.5 5.5 6 6"></path>

            <path d="m5 13 6 6"></path>

            <path d="m3.5 14.5 6 6"></path>

            <path d="M8.5 16.5 17 8"></path>

            <path d="M13 3.5 20.5 11"></path>

            <path d="M3 21h10"></path>

        </svg>

    `;

}


/* =========================================================
   CAMBIAR TEXTO DEL BOTÓN
   ========================================================= */

function cambiarTextoBotonSubastas(boton) {

    const walker =
        document.createTreeWalker(
            boton,
            NodeFilter.SHOW_TEXT
        );


    const textos = [];


    while (walker.nextNode()) {

        textos.push(
            walker.currentNode
        );

    }


    let cambiado =
        false;


    textos.forEach(

        function(nodo) {

            const texto =
                nodo.textContent;


            if (
                /gestión ganadera/i.test(texto) ||
                /gestion ganadera/i.test(texto) ||
                /subastas/i.test(texto)
            ) {

                nodo.textContent =
                    texto.replace(
                        /gestión ganadera|gestion ganadera|subastas/gi,
                        "Subastas"
                    );


                cambiado =
                    true;

            }

        }

    );


    if (!cambiado) {

        boton.appendChild(
            document.createTextNode(
                " Subastas"
            )
        );

    }

}


/* =========================================================
   CONFIGURAR VISUALMENTE BOTÓN SUBASTAS
   ========================================================= */

function prepararBotonSubastas(boton) {

    if (!boton) {
        return;
    }


    boton.setAttribute(
        "data-modulo",
        "SUBASTAS"
    );


    boton.classList.remove(
        "active"
    );


    boton.removeAttribute(
        "aria-current"
    );


    boton.removeAttribute(
        "target"
    );


    /*
       FINANZAS utiliza .menu-icon
       Los demás módulos utilizan .nav-icon

       Buscamos cualquiera de los dos.
    */

    let contenedorIcono =
        boton.querySelector(
            ".nav-icon, .menu-icon"
        );


    /*
       Si no existe ningún contenedor,
       lo creamos.
    */

    if (!contenedorIcono) {

        contenedorIcono =
            document.createElement(
                "span"
            );


        /*
           Elegimos la clase dependiendo
           del estilo del menú actual.
        */

        if (
            boton.closest(".menu")
        ) {

            contenedorIcono.className =
                "menu-icon";

        }
        else {

            contenedorIcono.className =
                "nav-icon";

        }


        boton.insertBefore(
            contenedorIcono,
            boton.firstChild
        );

    }


    /*
       IMPORTANTE:
       borramos completamente el icono
       original de Gestión Ganadera.
    */

    contenedorIcono.innerHTML = "";


    /*
       Colocamos únicamente el mazo
       de Subastas.
    */

    contenedorIcono.innerHTML = `

        <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
            style="
                width:20px;
                height:20px;
                display:block;
                fill:none;
                stroke:currentColor;
                stroke-width:1.8;
                stroke-linecap:round;
                stroke-linejoin:round;
            "
        >

            <path d="M14 4 20 10"></path>

            <path d="m12.5 5.5 6 6"></path>

            <path d="m5 13 6 6"></path>

            <path d="m3.5 14.5 6 6"></path>

            <path d="M8.5 16.5 17 8"></path>

            <path d="M13 3.5 20.5 11"></path>

            <path d="M3 21h10"></path>

        </svg>

    `;


    /*
       Cambiar solamente el texto
       Gestión ganadera → Subastas
    */

    const nodosTexto = [];


    const walker =
        document.createTreeWalker(
            boton,
            NodeFilter.SHOW_TEXT
        );


    while (
        walker.nextNode()
    ) {

        nodosTexto.push(
            walker.currentNode
        );

    }


    let textoCambiado =
        false;


    nodosTexto.forEach(

        function(nodo) {

            const texto =
                nodo.textContent;


            if (
                /gestión ganadera/i.test(texto) ||
                /gestion ganadera/i.test(texto)
            ) {

                nodo.textContent =
                    texto.replace(
                        /gestión ganadera|gestion ganadera/gi,
                        "Subastas"
                    );


                textoCambiado =
                    true;

            }

        }

    );


    /*
       Si el botón ya decía Subastas,
       no agregamos texto adicional.
    */

    const yaDiceSubastas =
        String(
            boton.textContent || ""
        )
        .replace(/\s+/g, " ")
        .trim()
        .toLowerCase()
        .includes("subastas");


    if (
        !textoCambiado &&
        !yaDiceSubastas
    ) {

        boton.appendChild(
            document.createTextNode(
                " Subastas"
            )
        );

    }


    /*
       Eliminamos cualquier navegación
       anterior del botón clonado.
    */

    boton.removeAttribute(
        "onclick"
    );


    /*
       Finanzas utiliza <a>.
    */

    if (
        boton.tagName
            .toUpperCase() === "A"
    ) {

        boton.href =
            obtenerRutaModulo(
                "SUBASTAS"
            );

    }


    /*
       Navegación definitiva.
    */

    boton.onclick =
        function(event) {

            if (event) {
                event.preventDefault();
            }


            irAModulo(
                "SUBASTAS"
            );

        };

}

/* =========================================================
   BUSCAR BOTONES SUBASTAS EXISTENTES
   ========================================================= */

function obtenerBotonesSubastasExistentes() {

    return Array.from(
        document.querySelectorAll(
            ".nav-item"
        )
    ).filter(

        function(elemento) {

            const modulo =
                String(
                    elemento.getAttribute(
                        "data-modulo"
                    ) || ""
                )
                .trim()
                .toUpperCase();


            const texto =
                String(
                    elemento.textContent || ""
                )
                .replace(/\s+/g, " ")
                .trim()
                .toLowerCase();


            return (
                modulo === "SUBASTAS" ||
                texto === "subastas"
            );

        }

    );

}


/* =========================================================
   CREAR / CORREGIR BOTÓN SUBASTAS
   ========================================================= */

function crearBotonSubastasSiHaceFalta() {

    const sesion =
        obtenerSesion();


    if (!sesion) {

        return;

    }


    const rol =
        String(
            sesion.rol || ""
        ).toUpperCase();


    /*
       Primero buscamos cualquier Subastas
       que ya exista en el HTML.
    */

    let botonesSubastas =
        obtenerBotonesSubastasExistentes();


    let botonSubastas =
        botonesSubastas.length
            ? botonesSubastas[0]
            : null;


    /*
       Si existen dos o más,
       dejamos solamente UNO.
    */

    if (
        botonesSubastas.length > 1
    ) {

        for (
            let i = 1;
            i < botonesSubastas.length;
            i++
        ) {

            botonesSubastas[i].remove();

        }

    }


    /*
       Si ya existe uno, lo corregimos:
       - mismo estilo
       - icono de mazo
       - navegación
       - data-modulo
    */

    if (botonSubastas) {

        prepararBotonSubastas(
            botonSubastas
        );

    }


    /*
       Si el rol NO puede usar Subastas,
       no creamos uno nuevo.

       Si existía en el HTML, ya tiene
       data-modulo SUBASTAS y configurarMenu()
       se encargará de ocultarlo.
    */

    if (
        rol !== "ADMIN" &&
        rol !== "GANADERIA"
    ) {

        return;

    }


    /*
       Si no existe ninguno,
       buscamos Gestión Ganadera y
       creamos Subastas a partir de su diseño.
    */

    if (!botonSubastas) {

        const botonGanaderia =
            document.querySelector(
                '[data-modulo="GANADERIA"]'
            );


        if (!botonGanaderia) {

            return;

        }


        botonSubastas =
            botonGanaderia.cloneNode(
                true
            );


        botonSubastas.removeAttribute(
            "id"
        );


        prepararBotonSubastas(
            botonSubastas
        );


        botonGanaderia
            .insertAdjacentElement(
                "afterend",
                botonSubastas
            );

    }
    else {

        /*
           Si ya existía, aseguramos que quede
           inmediatamente debajo de
           Gestión Ganadera.
        */

        const botonGanaderia =
            document.querySelector(
                '[data-modulo="GANADERIA"]'
            );


        if (
            botonGanaderia &&
            botonGanaderia.nextElementSibling !==
                botonSubastas
        ) {

            botonGanaderia
                .insertAdjacentElement(
                    "afterend",
                    botonSubastas
                );

        }

    }

}


/* =========================================================
   MARCAR SUBASTAS COMO ACTIVO
   ========================================================= */

function marcarSubastasActivo() {

    const archivo =
        window.location.pathname
            .replace(/\\/g, "/")
            .split("/")
            .pop()
            .toLowerCase();


    if (
        archivo !== "subastas.html" &&
        archivo !== "subastadetalle.html"
    ) {

        return;

    }


    const botonSubastas =
        document.querySelector(
            '[data-modulo="SUBASTAS"]'
        );


    if (!botonSubastas) {

        return;

    }


    document
        .querySelectorAll(
            ".nav-item.active"
        )
        .forEach(

            function(elemento) {

                elemento.classList.remove(
                    "active"
                );

            }

        );


    botonSubastas.classList.add(
        "active"
    );

}


/* =========================================================
   CONFIGURAR MENÚ SEGÚN PERMISOS
   ========================================================= */

function configurarMenu() {

    const sesion =
        obtenerSesion();


    if (!sesion) {

        return;

    }


    /*
       Primero normalizamos Subastas.
       Esto también elimina duplicados.
    */

    crearBotonSubastasSiHaceFalta();


    const elementos =
        document.querySelectorAll(
            "[data-modulo]"
        );


    elementos.forEach(

        function(elemento) {

            const modulo =
                String(
                    elemento.getAttribute(
                        "data-modulo"
                    ) || ""
                ).toUpperCase();


            if (
                tienePermiso(
                    modulo
                )
            ) {

                elemento.style.display =
                    "";

            }
            else {

                elemento.style.display =
                    "none";

            }

        }

    );


    marcarSubastasActivo();

}


/* =========================================================
   CONFIGURACIÓN AUTOMÁTICA
   ========================================================= */

document.addEventListener(

    "DOMContentLoaded",

    function() {

        configurarMenu();

    }

);