package com.example.cosmos.ui.Orbit

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MINI DICCIONARIO — lee esto antes de leer el código
 * ═══════════════════════════════════════════════════════════════════
 *
 *  LayoutManager custom — curva parabólica
 *      En vez de colocar las cards en una lista vertical recta, las
 *      desplaza horizontalmente según su distancia al centro de la
 *      pantalla. Las cards centrales se alinean al centro, las de
 *      arriba/abajo se desplazan creando una forma de parábola.
 *      El desplazamiento máximo es 64dp (configurable).
 *
 *  onLayoutChildren / scrollVerticallyBy
 *      onLayoutChildren: posiciona todos los hijos cuando el RV
 *      se dibuja por primera vez o cambia de tamaño.
 *      scrollVerticallyBy: recalcula el offset X de cada hijo
 *      cuando el usuario hace scroll vertical.
 *
 *  GUÍA DE AJUSTE RÁPIDO
 *      density * 64f (línea ~38):
 *          Desplazamiento máximo en dp de las cards centrales.
 *          Subir (ej: 96f) = curva más pronunciada.
 *          Bajar (ej: 32f) = curva más suave.
 *      1f - normalized * normalized (línea ~64):
 *          Forma de la curva. normalized² = parábola suave (U).
 *          Cambiar a normalized (sin cuadrado) = V recta.
 *          Invertir a normalized * normalized = extremos desplazados, centro quieto.
 *      Signo de maxShiftPx:
 *          Positivo = cards centrales a la derecha.
 *          Negativo = cards centrales a la izquierda.
 * ═══════════════════════════════════════════════════════════════════
 */

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * LayoutManager parabólico: los ítems del centro se desplazan a la derecha (maxShiftPx)
 * y los de los extremos quedan al ras izquierdo, creando una curva izquierda→centro→izquierda.
 */
class OrbitalLayoutManager(context: Context) : LinearLayoutManager(context) {

    private var maxShiftPx = 0f

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        maxShiftPx = view.context.resources.displayMetrics.density * 64f
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        applyTranslations()
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        applyTranslations()
        return scrolled
    }

    private fun applyTranslations() {
        val midY = height / 2f
        if (midY == 0f) return
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val childMidY = (getDecoratedTop(child) + getDecoratedBottom(child)) / 2f
            val normalized = (abs(childMidY - midY) / midY).coerceIn(0f, 1f)
            // Parábola: máximo en centro (normalized=0), cero en extremos (normalized=1)
            child.translationX = maxShiftPx * (1f - normalized * normalized)
        }
    }
}
