package io.github.cataylor5.ibs

import com.google.android.libraries.places.api.model.Place
import java.lang.reflect.Method

fun printPlaceMethods() {
    val methods = Place::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
