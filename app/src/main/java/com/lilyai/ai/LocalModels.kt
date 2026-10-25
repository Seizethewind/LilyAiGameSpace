package com.lilyai.ai

/** A model the LilyAi page can download. */
data class LocalModel(
    val id: String,
    val name: String,
    val note: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long
)

// The models offered on the LilyAi page. Both are Qwen3 builds in the .litertlm format from
// Hugging Face's litert-community, which are public (Apache-2.0). To offer another model, add
// an entry here with its file link and size.
object LocalModels {
    val all = listOf(
        LocalModel(
            id = "qwen3-0.6b",
            name = "Qwen3 0.6B",
            note = "The smallest and quickest. A good first test of what your phone can run.",
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
            approxBytes = 497_700_000L
        ),
        LocalModel(
            id = "qwen3-4b",
            name = "Qwen3 4B",
            note = "Smarter than the 0.6B, but bigger and slower. It needs several GB of memory while running.",
            fileName = "qwen3_4b_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm",
            approxBytes = 2_659_100_000L
        )
    )
}
