pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ketchup-player"

include(":app")

// Moduły wg docs/KONSPEKT.md §3.2
val modules = listOf(
    ":core:model", ":core:policy", ":core:common", ":core:network", ":core:database",
    ":core:datastore", ":core:security", ":core:data", ":core:designsystem", ":core:ui",
    ":core:testing",
    ":provider:api", ":provider:m3u", ":provider:xtream", ":provider:stalker",
    ":epg:xmltv", ":epg:teleman", ":epg:radar",
    ":player:engine", ":player:failover",
    ":feature:home", ":feature:guide", ":feature:vod", ":feature:series",
    ":feature:details", ":feature:player-ui", ":feature:search", ":feature:mylist",
    ":feature:settings", ":feature:providers", ":feature:profiles", ":feature:recording",
    ":feature:onboarding",
    ":integration:cast", ":integration:vpn", ":integration:nas", ":integration:tvprovider",
)
modules.forEach { include(it) }
