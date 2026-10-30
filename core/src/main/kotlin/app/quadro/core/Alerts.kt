package app.quadro.core

enum class AlertState { OK, LOW, HIGH }

data class AlertDecision(val state: AlertState, val notify: Boolean)

/**
 * Valuta una soglia con isteresi. Si notifica solo quando si entra in LOW o HIGH da uno stato diverso.
 * Per tornare a OK il valore deve rientrare di almeno [hysteresis] oltre il limite superato.
 */
fun evaluateAlert(
    value: Double,
    rule: AlertRule,
    previous: AlertState,
    hysteresis: Double,
): AlertDecision {
    if (!rule.enabled) return AlertDecision(AlertState.OK, false)
    val min = rule.min
    val max = rule.max
    val raw = when {
        min != null && value < min -> AlertState.LOW
        max != null && value > max -> AlertState.HIGH
        else -> AlertState.OK
    }
    val state = when {
        raw == AlertState.OK && previous == AlertState.LOW && min != null && value < min + hysteresis -> AlertState.LOW
        raw == AlertState.OK && previous == AlertState.HIGH && max != null && value > max - hysteresis -> AlertState.HIGH
        else -> raw
    }
    return AlertDecision(state, notify = state != AlertState.OK && state != previous)
}
