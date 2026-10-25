package com.lilyai.data.cloud

// The two Turso databases LilyAi can connect to. Each one has its own address and token,
// set up on its own page in Secrets.
enum class TursoDatabase(
    val id: String,
    val displayName: String,
    val icon: String,
    val purpose: String
) {
    GAME_DATA(
        "game_data",
        "Turso - Game data",
        "\uD83D\uDCBE",
        "Stores your games and your strategy for each game. Every game gets its own entry."
    ),
    AI_DATA(
        "ai_data",
        "Turso - AI data",
        "\uD83E\uDDE0",
        "Stores what the AI remembers, such as chats and notes. The AI itself runs on this phone."
    )
}
