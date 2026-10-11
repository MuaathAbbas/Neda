package com.adhan.app.domain

/**
 * Every alert the app can raise. New kinds get a new constant and a
 * matching [Schedule] variant; they still end in [com.adhan.app.alert.AlertPlayer].
 */
enum class AlertType {
    TIME,
    PRAYER,
    PLACE,
}
