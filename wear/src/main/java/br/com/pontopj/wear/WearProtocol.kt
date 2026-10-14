package br.com.pontopj.wear

/**
 * Contrato entre o celular e o relógio. Cópia de app/.../sync/WearProtocol.kt;
 * se mudar um, mude o outro.
 */
object WearProtocol {
    const val PATH_STATE = "/ponto/state"

    const val PATH_CLOCK_IN = "/ponto/cmd/clock_in"
    const val PATH_CLOCK_OUT = "/ponto/cmd/clock_out"
    const val PATH_REQUEST_STATE = "/ponto/cmd/request_state"

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
