package com.kakao.gitnewtagextended

data class GitTagExtended(
    val refname: String = "",
    val creator: String = "",
    val creatordate: String = "",
    val order: Int = 0,
) {
    override fun toString(): String {
        return refname
    }
}
