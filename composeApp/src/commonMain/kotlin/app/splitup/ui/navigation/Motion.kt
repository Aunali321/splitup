package app.splitup.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

/**
 * Hand-crafted navigation motion. Three flavours:
 *  - forward    : opening a detail screen — slide left, paired with parent
 *                 fading and shrinking inward slightly.
 *  - modal      : add-expense / settle-up / import / settings — slide up
 *                 from the bottom of the screen, parent stays put.
 *  - onboarding : slide forward (right-to-left), no parallax.
 *
 * Durations follow Material 3 motion guidance: 350ms for the entering screen,
 * 250ms for the exiting one. FastOutSlowInEasing matches the system default
 * for "user-initiated" transitions.
 */

private const val ENTER_MS = 350
private const val EXIT_MS = 250
private val easing = FastOutSlowInEasing

object Motion {
    val forwardEnter: NavEnter = {
        slideInHorizontally(tween(ENTER_MS, easing = easing)) { full -> (full * 0.18f).toInt() } +
            fadeIn(tween(ENTER_MS, easing = easing))
    }
    val forwardExit: NavExit = {
        slideOutHorizontally(tween(EXIT_MS, easing = easing)) { full -> -(full * 0.08f).toInt() } +
            fadeOut(tween(EXIT_MS, easing = easing))
    }
    val backEnter: NavEnter = {
        slideIntoContainer(SlideDirection.End, tween(ENTER_MS, easing = easing)) +
            fadeIn(tween(ENTER_MS, easing = easing))
    }
    val backExit: NavExit = {
        slideOutOfContainer(SlideDirection.End, tween(EXIT_MS, easing = easing)) +
            fadeOut(tween(EXIT_MS, easing = easing))
    }

    val modalEnter: NavEnter = {
        slideInVertically(tween(ENTER_MS, easing = easing)) { full -> full } +
            fadeIn(tween(ENTER_MS, easing = easing))
    }
    val modalExit: NavExit = { ExitTransition.None }
    val modalPopEnter: NavEnter = { EnterTransition.None }
    val modalPopExit: NavExit = {
        slideOutVertically(tween(EXIT_MS, easing = easing)) { full -> full } +
            fadeOut(tween(EXIT_MS, easing = easing))
    }

    val onboardingEnter: NavEnter = {
        slideIntoContainer(SlideDirection.Start, tween(ENTER_MS, easing = easing))
    }
    val onboardingExit: NavExit = {
        slideOutOfContainer(SlideDirection.Start, tween(EXIT_MS, easing = easing))
    }
    val onboardingPopEnter: NavEnter = {
        slideIntoContainer(SlideDirection.End, tween(ENTER_MS, easing = easing))
    }
    val onboardingPopExit: NavExit = {
        slideOutOfContainer(SlideDirection.End, tween(EXIT_MS, easing = easing))
    }

    val tabFade: NavEnter = { fadeIn(tween(180)) }
    val tabFadeExit: NavExit = { fadeOut(tween(120)) }
}

typealias NavEnter = androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
typealias NavExit = androidx.compose.animation.AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition
