package com.gestionbovina.gestionbovina.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Clase padre: comportamiento compartido de los cálculos de planilla. */
public abstract class PlanillaBase {
    protected BigDecimal salarioMensual;

    protected PlanillaBase(BigDecimal salarioMensual) {
        if (salarioMensual == null || salarioMensual.signum() < 0) {
            throw new IllegalArgumentException("El salario debe ser válido.");
        }
        this.salarioMensual = salarioMensual;
    }

    protected BigDecimal redondear(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    public abstract BigDecimal calcular();
}

