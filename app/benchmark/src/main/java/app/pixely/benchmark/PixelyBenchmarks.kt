package app.pixely.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start, grid scrolling and a search, each without and with the baseline profile. */
@RunWith(AndroidJUnit4::class)
class PixelyBenchmarks {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupNoProfile() = startup(CompilationMode.None())
    @Test fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))
    @Test fun scrollNoProfile() = scroll(CompilationMode.None())
    @Test fun scrollWithProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))
    @Test fun searchNoProfile() = search(CompilationMode.None())
    @Test fun searchWithProfile() = search(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 8,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // No startupMode: it would close the app between setupBlock and the measured part.
        iterations = 5,
        // A fresh launch each time, so every run starts at the top of the grid.
        setupBlock = { killProcess(); startActivityAndWait() },
    ) {
        scrollPhotos()
    }

    private fun search(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        // No startupMode: it would close the app between setupBlock and the measured part.
        iterations = 5,
        // A fresh launch each time, so every run starts at the top of the grid.
        setupBlock = { killProcess(); startActivityAndWait() },
    ) {
        search("dog on a beach")
    }
}
