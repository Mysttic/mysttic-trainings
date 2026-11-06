package pl.mysttic.trainings.ui.common

import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Własne ikony (D-52): każda ścieżka z projektu musi się sparsować w siatce 24 x 24, inaczej ikona wyjdzie pusta. */
class AppIconsTest {

    @Test
    fun `kazda ikona ma niepusta sciezke w siatce 24`() {
        AppIcons.all.forEach { (name, icon) ->
            assertEquals(name, 24f, icon.viewportWidth)
            assertEquals(name, 24f, icon.viewportHeight)
            val paths = (0 until icon.root.size).map { icon.root[it] }.filterIsInstance<VectorPath>()
            assertTrue("$name: brak ścieżki", paths.isNotEmpty())
            assertTrue("$name: pusta ścieżka", paths.all { it.pathData.isNotEmpty() })
        }
    }

    @Test
    fun `nazwy ikon sa unikalne`() {
        val names = AppIcons.all.values.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }
}
