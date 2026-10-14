package br.com.pontopj.sync

/**
 * Contrato entre o celular e o relógio. O mesmo arquivo existe nos dois módulos
 * (app/ e wear/); se mudar um, mude o outro.
 */
object WearProtocol {
    // Celular -> relógio (DataClient): estado atual.
    const val PATH_STATE = "/ponto/state"

    // Relógio -> celular (MessageClient): comandos.
    const val PATH_CLOCK_IN = "/ponto/cmd/clock_in"
    const val PATH_CLOCK_OUT = "/ponto/cmd/clock_out"
    const val PATH_REQUEST_STATE = "/ponto/cmd/request_state"

    // Chaves do estado.
    const val K_ACTIVE = "active"
    const val K_OPEN_START = "open_start"
    const val K_OPEN_RATE = "open_rate"
    const val K_DAY_BASE_MILLIS = "day_base_millis"
    const val K_DAY_BASE_EARN = "day_base_earn"
    const val K_MONTH_BASE_EARN = "month_base_earn"
    const val K_MONTH_BASE_MILLIS = "month_base_millis"
    const val K_MONTH_GOAL = "month_goal"
    const val K_GOAL_HOURS = "goal_hours"
    const val K_HIDE = "hide"
    const val K_ACCENT = "accent"
    const val K_UPDATED_AT = "updated_at"
}
