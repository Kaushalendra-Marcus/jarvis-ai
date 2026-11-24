package ai.jarvis.app.presentation.integrations

import ai.jarvis.app.R

/** How a catalog tile connects. */
enum class CatalogKind { GMAIL, CALENDAR, CUSTOM_SOON }

/**
 * Top-10 app catalog for Links. Gmail is live, Calendar is beta, the rest connect
 * through a guided custom MCP/OpenAPI setup until native adapters land.
 */
data class CatalogApp(
    val id: String,
    val name: String,
    /** What Jarvis can do with it once connected — the data story, in one line. */
    val dataUses: String,
    val iconRes: Int,
    val kind: CatalogKind
)

val TOP_APPS = listOf(
    CatalogApp(
        "gmail", "Gmail",
        "Search, read, draft and send mail — sending always asks first.",
        R.drawable.ic_brand_gmail, CatalogKind.GMAIL
    ),
    CatalogApp(
        "google-calendar", "Calendar",
        "List and create events once the Calendar scope lands.",
        R.drawable.ic_brand_calendar, CatalogKind.CALENDAR
    ),
    CatalogApp(
        "drive", "Drive",
        "Find, read and summarize your documents.",
        R.drawable.ic_brand_drive, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "outlook", "Outlook",
        "Triage and draft replies in your Outlook inbox.",
        R.drawable.ic_brand_outlook, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "slack", "Slack",
        "Read channels and post updates for you.",
        R.drawable.ic_brand_slack, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "telegram", "Telegram",
        "Send alerts and answer from your chats.",
        R.drawable.ic_brand_telegram, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "notion", "Notion",
        "Read pages and file notes where you keep them.",
        R.drawable.ic_brand_notion, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "spotify", "Spotify",
        "Queue playlists and control playback by voice.",
        R.drawable.ic_brand_spotify, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "youtube", "YouTube",
        "Summarize videos and queue what to watch.",
        R.drawable.ic_brand_youtube, CatalogKind.CUSTOM_SOON
    ),
    CatalogApp(
        "linkedin", "LinkedIn",
        "Draft posts and summarize your network updates.",
        R.drawable.ic_brand_linkedin, CatalogKind.CUSTOM_SOON
    )
)

fun catalogFor(id: String): CatalogApp? = TOP_APPS.find { it.id == id }
