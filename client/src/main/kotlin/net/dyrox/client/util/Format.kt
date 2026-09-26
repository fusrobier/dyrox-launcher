package net.dyrox.client.util

import java.util.Locale

/** Formats with a dot as decimal separator whatever the system locale ("3.0", not "3,0"). */
fun Float.format(decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", this)
