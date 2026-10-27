plugins {
    id("moyi.kmp.library")
    id("moyi.dependency.guard")
}

// Core is what domain sees through `api`, so a framework added here would reach
// domain transitively. The guard keeps core to the same allow-list (stdlib,
// coroutines); widen it here deliberately (e.g. Kermit, datetime) when needed.
